from __future__ import annotations

import asyncio
import contextvars
from collections.abc import Callable, Generator
from typing import Any, Generic, Protocol, TypeVar

from .types import wire


class Bridge(Protocol):
    def submit(
        self, operation: str, target: dict[str, Any], arguments: dict[str, Any], owner: str
    ) -> OperationReceipt: ...


T = TypeVar("T")
U = TypeVar("U")


class OperationReceipt(Generic[T]):
    """Await to confirm execution; unawaited errors are reported by the runtime."""

    def __init__(self, future: asyncio.Future[Any], transform: Callable[[Any], T] | None = None):
        self._future = future
        self._transform = transform

    def __await__(self) -> Generator[Any, None, T]:
        async def resolve() -> T:
            value = await asyncio.shield(self._future)
            return self._transform(value) if self._transform else value

        return resolve().__await__()

    def map(self, transform: Callable[[T], U]) -> OperationReceipt[U]:
        return OperationReceipt(
            self._future,
            lambda value: transform(self._transform(value) if self._transform else value),
        )


_bridge: Bridge | None = None
owner_context: contextvars.ContextVar[str] = contextvars.ContextVar(
    "pynner_owner", default="unknown"
)


def bind(bridge: Bridge | None) -> None:
    global _bridge
    _bridge = bridge


def request(
    operation: str, target: dict[str, Any] | None = None, **arguments: Any
) -> OperationReceipt:
    if _bridge is None:
        raise RuntimeError(
            "Minecraft operations require an active Pynner runtime; on_load is registration-only"
        )
    return _bridge.submit(operation, target or {}, wire(arguments), owner_context.get())
