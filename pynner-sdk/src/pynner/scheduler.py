from dataclasses import dataclass

from . import _registry
from ._bridge import OperationReceipt, request


@dataclass(frozen=True)
class TaskHandle:
    id: str

    def cancel(self) -> OperationReceipt:
        return request("scheduler.cancel", id=self.id)


def _schedule(seconds: float, repeating: bool):
    if seconds <= 0:
        raise ValueError("seconds must be positive")

    def decorate(callback):
        ident = _registry.registry.add(callback, kind="task", seconds=seconds, repeating=repeating)
        callback.task = TaskHandle(ident)
        return callback

    return decorate


def every(*, seconds: float):
    """Game-time interval: seconds are converted to 20 ticks per second."""
    return _schedule(seconds, True)


def delay(*, seconds: float):
    return _schedule(seconds, False)
