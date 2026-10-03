from __future__ import annotations

import inspect
import re
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, TypeVar, get_type_hints

from . import _registry
from ._bridge import OperationReceipt, request
from .player import Player

F = TypeVar("F", bound=Callable[..., Any])


@dataclass(frozen=True)
class CommandContext:
    sender: str
    sender_name: str
    player: Player | None
    args: tuple[str, ...] = ()

    def reply(self, message: str) -> OperationReceipt:
        return request("command.reply", {"sender": self.sender}, message=message)


def command(
    name: str,
    *,
    aliases: list[str] | None = None,
    permission: str | None = None,
    player_only: bool = False,
    completions: dict[str, list[str]] | None = None,
) -> Callable[[F], F]:
    labels = [name, *(aliases or [])]
    if any(not re.fullmatch(r"[a-z][a-z0-9_-]*", label) for label in labels):
        raise ValueError("Invalid command label")

    def decorate(callback: F) -> F:
        hints = get_type_hints(callback)
        parameters = list(inspect.signature(callback).parameters.values())
        if not parameters:
            raise ValueError("Commands require a context argument")
        arguments: list[dict[str, Any]] = []
        for parameter in parameters[1:]:
            annotation = hints.get(parameter.name, str)
            if annotation not in (str, int, float, bool, Player):
                raise ValueError(f"Unsupported command argument type: {annotation}")
            arguments.append(
                {
                    "name": parameter.name,
                    "type": annotation.__name__,
                    "required": parameter.default is inspect.Parameter.empty,
                    "default": None
                    if parameter.default is inspect.Parameter.empty
                    else parameter.default,
                }
            )
        for handler in _registry.registry.handlers.values():
            if handler.metadata.get("kind") == "command" and set(labels) & set(
                [handler.metadata["name"], *handler.metadata["aliases"]]
            ):
                raise ValueError("Duplicate command label")
        _registry.registry.add(
            callback,
            kind="command",
            name=name,
            aliases=aliases or [],
            permission=permission,
            player_only=player_only,
            arguments=arguments,
            completions=completions or {},
        )
        return callback

    return decorate
