from __future__ import annotations

import asyncio
import inspect
import logging
import traceback
from pathlib import Path
from typing import Any

from pynner import _registry
from pynner._bridge import OperationReceipt, bind, owner_context
from pynner.command import CommandContext
from pynner.entity import Entity
from pynner.errors import BridgeError
from pynner.event import decode_event
from pynner.player import Player

from .loader import ScriptLoader
from .transport import encode, read, write


class Worker:
    def __init__(
        self, host: str, port: int, token: str, generation: int, scripts: Path, logs: Path
    ):
        self.host, self.port, self.token, self.generation = host, port, token, generation
        self.loader = ScriptLoader(scripts)
        self.logs = logs
        self.pending: dict[int, asyncio.Future[Any]] = {}
        self.outgoing: asyncio.Queue[dict[str, Any]] = asyncio.Queue(1024)
        self.incoming: asyncio.PriorityQueue[tuple[int, int, dict[str, Any]]] = (
            asyncio.PriorityQueue(1024)
        )
        self.invocation_sequence = 0
        self.counter = 0
        self.active = False
        self.completed = 0
        self.dropped = 0
        self.incoming_bytes = 0
        self.failures: dict[str, int] = {}
        self.stopping = asyncio.Event()

    def message(self, kind: str, payload: dict[str, Any]) -> dict[str, Any]:
        return {"version": 1, "type": kind, "generation": self.generation, "payload": payload}

    def log_error(self, owner: str, handler: str, detail: str | None = None) -> None:
        message = f"[Pynner] Python Error\nscript: {owner}\nhandler: {handler}\n{detail or traceback.format_exc()}"
        logging.error(message)
        try:
            self.outgoing.put_nowait(self.message("log", {"message": message[:32000]}))
        except asyncio.QueueFull:
            logging.error("Bridge log queue full")

    def submit(
        self, operation: str, target: dict[str, Any], arguments: dict[str, Any], owner: str
    ) -> OperationReceipt:
        if not self.active:
            raise RuntimeError("Minecraft operations are unavailable before activation")
        if len(self.pending) >= 1024:
            raise BridgeError("QUEUE_FULL", "Too many pending operations")
        self.counter += 1
        ident = self.counter
        future = asyncio.get_running_loop().create_future()
        self.pending[ident] = future
        payload = {
            "request_id": ident,
            "operation": operation,
            "target": target,
            "arguments": arguments,
            "owner": owner,
            "timeout_ms": 5000,
        }
        envelope = self.message("operation", payload)
        try:
            if len(encode(envelope)) > 65536:
                raise ValueError("Operation exceeds 64 KiB")
        except (ValueError, TypeError) as error:
            self.pending.pop(ident)
            future.cancel()
            raise BridgeError("INVALID_ARGUMENT", str(error)) from error
        try:
            self.outgoing.put_nowait(envelope)
        except asyncio.QueueFull:
            self.pending.pop(ident)
            raise BridgeError("QUEUE_FULL", "Control queue full") from None

        def expire() -> None:
            pending = self.pending.pop(ident, None)
            if pending is not None and not pending.done():
                pending.set_exception(
                    BridgeError(
                        "TIMEOUT", "No response within 5 seconds; execution may have occurred"
                    )
                )

        timer = asyncio.get_running_loop().call_later(5, expire)

        def finish(done: asyncio.Future[Any]) -> None:
            timer.cancel()
            self.pending.pop(ident, None)
            if not done.cancelled() and done.exception() is not None:
                self.log_error(owner, operation, str(done.exception()))

        future.add_done_callback(finish)
        return OperationReceipt(future)

    async def connect(self, channel: str):
        reader, writer = await asyncio.open_connection(self.host, self.port)
        await write(writer, self.message("hello", {"token": self.token, "channel": channel}))
        reply = await asyncio.wait_for(read(reader), 10)
        if reply.get("type") != "welcome" or reply.get("version") != 1:
            raise RuntimeError("Bridge handshake failed")
        return reader, writer

    async def send_loop(self, writer) -> None:
        while not self.stopping.is_set():
            await write(writer, await self.outgoing.get())

    async def control_loop(self, reader) -> None:
        while True:
            envelope = await read(reader)
            if envelope.get("version") != 1:
                raise ValueError("Unsupported protocol version")
            if envelope.get("generation") != self.generation:
                continue
            kind, payload = envelope["type"], envelope["payload"]
            if kind == "activate":
                self.active = True
                bind(self)
                await self.loader.lifecycle("on_enable", self.log_error)
                self.outgoing.put_nowait(self.message("ready", {}))
            elif kind == "result":
                future = self.pending.pop(payload["request_id"], None)
                if future is not None and not future.done():
                    if payload.get("error"):
                        future.set_exception(
                            BridgeError(payload["error"], payload.get("message", ""))
                        )
                    else:
                        future.set_result(payload.get("value"))
            elif kind == "shutdown":
                await self.loader.lifecycle("on_disable", self.log_error)
                self.active = False
                bind(None)
                self.stopping.set()
                return

    async def events_loop(self, reader) -> None:
        while True:
            message = await read(reader)
            if message.get("version") != 1 or message.get("type") != "events":
                raise ValueError("Invalid event envelope")
            if message.get("generation") != self.generation or not self.active:
                continue
            for invocation in message.get("payload", {}).get("invocations", []):
                try:
                    size = len(encode(invocation))
                    if self.incoming_bytes + size > 8 * 1024 * 1024:
                        raise asyncio.QueueFull
                    invocation["_wire_bytes"] = size
                    self.invocation_sequence += 1
                    event_name = invocation.get("event", {}).get("event", "")
                    priority = 1 if event_name in {"player_move", "mob_move", "mob_tick"} else 0
                    self.incoming.put_nowait((priority, self.invocation_sequence, invocation))
                    self.incoming_bytes += size
                except asyncio.QueueFull:
                    self.dropped += 1
                    handler = _registry.registry.handlers.get(invocation.get("handler", ""))
                    if handler is not None and handler.metadata["kind"] == "task":
                        self.outgoing.put_nowait(self.message("task_done", {"id": handler.id}))

    async def dispatch(self, invocation: dict[str, Any]) -> None:
        handler = _registry.registry.handlers.get(invocation["handler"])
        if handler is None or self.failures.get(handler.id, 0) >= 5:
            return
        token = owner_context.set(handler.owner)
        try:
            arguments: list[Any]
            kind = handler.metadata["kind"]
            if kind == "task":
                arguments = []
            elif kind == "command":
                context = invocation["context"]
                player = Player(context["player"]) if context.get("player") else None
                arguments = [
                    CommandContext(
                        context["sender"],
                        context["sender_name"],
                        player,
                        tuple(context.get("args", [])),
                    )
                ]
                for value, spec in zip(invocation["arguments"], handler.metadata["arguments"]):
                    arguments.append(
                        Player(value) if spec["type"] == "Player" and value is not None else value
                    )
            elif kind == "mob" and handler.metadata["hook"] in ("on_spawn", "on_tick"):
                arguments = [Entity(invocation["event"]["entity"])]
            else:
                arguments = [decode_event(invocation["event"])]
            result = handler.callback(*arguments)
            if inspect.isawaitable(result):
                await asyncio.wait_for(result, 5)
            self.failures[handler.id] = 0
        except Exception:
            self.failures[handler.id] = self.failures.get(handler.id, 0) + 1
            self.log_error(handler.owner, handler.callback.__qualname__)
        finally:
            self.completed += 1
            if handler.metadata["kind"] == "task":
                try:
                    self.outgoing.put_nowait(self.message("task_done", {"id": handler.id}))
                except asyncio.QueueFull:
                    self.log_error(handler.owner, handler.id, "Task acknowledgement queue full")
            owner_context.reset(token)

    async def dispatch_loop(self) -> None:
        while True:
            _, _, invocation = await self.incoming.get()
            self.incoming_bytes -= invocation.pop("_wire_bytes", 0)
            await self.dispatch(invocation)

    async def heartbeat(self) -> None:
        while True:
            await asyncio.sleep(1)
            self.outgoing.put_nowait(
                self.message(
                    "heartbeat",
                    {
                        "completed": self.completed,
                        "queue": self.incoming.qsize(),
                        "dropped": self.dropped,
                    },
                )
            )

    async def run(self) -> None:
        self.logs.mkdir(parents=True, exist_ok=True)
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s %(message)s",
            handlers=[
                logging.FileHandler(self.logs / f"python-{self.generation}.log", encoding="utf-8"),
                logging.StreamHandler(),
            ],
        )
        control_reader, control_writer = await self.connect("control")
        event_reader, event_writer = await self.connect("events")
        tasks = []
        try:
            try:
                self.loader.load()
                await self.loader.lifecycle("on_load", self.log_error, fail_fast=True)
                await write(control_writer, self.message("register", self.loader_manifest()))
            except Exception:
                await write(
                    control_writer, self.message("load_error", {"message": traceback.format_exc()})
                )
                raise
            tasks = [
                asyncio.create_task(coro)
                for coro in (
                    self.send_loop(control_writer),
                    self.control_loop(control_reader),
                    self.events_loop(event_reader),
                    self.dispatch_loop(),
                    self.heartbeat(),
                    self.stopping.wait(),
                )
            ]
            done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in done:
                task.result()
        finally:
            bind(None)
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            for future in self.pending.values():
                if not future.done():
                    future.cancel()
            control_writer.close()
            event_writer.close()
            await asyncio.gather(
                control_writer.wait_closed(), event_writer.wait_closed(), return_exceptions=True
            )

    def loader_manifest(self) -> dict[str, Any]:
        return _registry.registry.manifest()
