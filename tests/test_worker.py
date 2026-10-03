import asyncio
from pathlib import Path

from pynner_runtime.transport import read, write
from pynner_runtime.worker import Worker


def test_worker_two_channels_operations_commands_and_shutdown(tmp_path: Path):
    (tmp_path / "plugin.py").write_text(
        "from pynner import event, command, scheduler, CommandContext\n"
        '@event("player_join")\n'
        'def join(e): e.player.send_message("hello")\n'
        '@command("number")\n'
        "async def number(ctx: CommandContext, amount: int): await ctx.reply(str(amount))\n"
        "@scheduler.delay(seconds=1)\n"
        "def timer(): pass\n"
        'def on_disable(): print("disabled")\n',
        encoding="utf-8",
    )

    async def run():
        writers = {}
        messages = asyncio.Queue()
        handlers = set()

        async def serve(reader, writer):
            try:
                hello = await read(reader)
                assert hello["payload"]["token"] == "secret"
                channel = hello["payload"]["channel"]
                writers[channel] = writer
                handlers.add(asyncio.current_task())
                await write(
                    writer, {"version": 1, "type": "welcome", "generation": 1, "payload": {}}
                )
                while True:
                    message = await read(reader)
                    await messages.put(message)
                    if message["type"] == "operation":
                        await write(
                            writer,
                            {
                                "version": 1,
                                "type": "result",
                                "generation": 1,
                                "payload": {
                                    "request_id": message["payload"]["request_id"],
                                    "value": True,
                                },
                            },
                        )
            except asyncio.IncompleteReadError:
                pass
            finally:
                writer.close()
                await writer.wait_closed()

        async def next_message(kind):
            while True:
                message = await asyncio.wait_for(messages.get(), 3)
                if message["type"] == kind:
                    return message["payload"]

        async def send(channel, kind, payload):
            await write(
                writers[channel], {"version": 1, "type": kind, "generation": 1, "payload": payload}
            )

        server = await asyncio.start_server(serve, "127.0.0.1", 0)
        port = server.sockets[0].getsockname()[1]
        worker = Worker("127.0.0.1", port, "secret", 1, tmp_path, tmp_path / "logs")
        task = asyncio.create_task(worker.run())
        try:
            manifest = await next_message("register")
            by_kind = {h["kind"]: h for h in manifest["handlers"]}
            await send("control", "activate", {})
            await next_message("ready")
            await send(
                "events",
                "events",
                {
                    "invocations": [
                        {
                            "handler": by_kind["event"]["id"],
                            "event": {
                                "event": "player_join",
                                "player": {"uuid": "test", "type": "PLAYER"},
                            },
                        }
                    ]
                },
            )
            operation = await next_message("operation")
            assert operation["operation"] == "player.send_message"
            assert operation["arguments"]["message"] == "hello"
            await send(
                "events",
                "events",
                {
                    "invocations": [
                        {
                            "handler": by_kind["command"]["id"],
                            "context": {"sender": "console", "sender_name": "Console"},
                            "arguments": [42],
                        }
                    ]
                },
            )
            operation = await next_message("operation")
            assert operation["operation"] == "command.reply"
            assert operation["arguments"]["message"] == "42"
            await send("events", "events", {"invocations": [{"handler": by_kind["task"]["id"]}]})
            assert (await next_message("task_done"))["id"] == by_kind["task"]["id"]
            await send("control", "shutdown", {})
            await asyncio.wait_for(task, 3)
            assert not worker.active
        finally:
            if not task.done():
                task.cancel()
            await asyncio.gather(task, return_exceptions=True)
            server.close()
            await server.wait_closed()
            await asyncio.gather(*handlers, return_exceptions=True)

    asyncio.run(run())
