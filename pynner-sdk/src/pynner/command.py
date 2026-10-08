from __future__ import annotations

import inspect
import math
import re
from collections.abc import Callable, Sequence
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
    choices: dict[str, Sequence[str | int | float | bool]] | None = None,
    ranges: dict[str, tuple[int | float | None, int | float | None]] | None = None,
    error_messages: dict[str, str] | None = None,
) -> Callable[[F], F]:
    labels = [name, *(aliases or [])]
    if any(not re.fullmatch(r"[a-z][a-z0-9_-]*", label) for label in labels):
        raise ValueError("Invalid command label")

    def decorate(callback: F) -> F:
        hints = get_type_hints(callback)
        parameters = list(inspect.signature(callback).parameters.values())
        if not parameters:
            raise ValueError("Commands require a context argument")
        names = {parameter.name for parameter in parameters[1:]}
        for settings in (choices, ranges):
            if settings and set(settings) - names:
                raise ValueError("Constraint refers to an unknown command argument")
        messages = dict(error_messages or {})
        codes = {
            "missing",
            "invalid",
            "choices",
            "range",
            "too_many",
            "player_only",
            "permission",
            "unavailable",
        }
        for key, value in messages.items():
            prefix, separator, code = key.rpartition(".")
            if (
                code not in codes
                or (separator and prefix not in names)
                or not isinstance(value, str)
            ):
                raise ValueError("Invalid command error_messages key or value")
        arguments: list[dict[str, Any]] = []
        for parameter in parameters[1:]:
            annotation = hints.get(parameter.name, str)
            if annotation not in (str, int, float, bool, Player):
                raise ValueError(f"Unsupported command argument type: {annotation}")
            allowed = None
            if choices and parameter.name in choices:
                values = choices[parameter.name]
                if isinstance(values, (str, bytes)) or not values:
                    raise ValueError("choices requires a non-empty sequence")
                allowed = list(values)
                if annotation is Player or any(
                    type(value) is not annotation
                    or (isinstance(value, float) and not math.isfinite(value))
                    for value in allowed
                ):
                    raise ValueError("choices must match the argument type")
            lower = upper = None
            if ranges and parameter.name in ranges:
                bounds = ranges[parameter.name]
                if annotation not in (int, float) or len(bounds) != 2:
                    raise ValueError("ranges requires a numeric argument and two bounds")
                lower, upper = bounds
                if any(
                    value is not None
                    and (type(value) not in (int, float) or not math.isfinite(value))
                    for value in bounds
                ):
                    raise ValueError("Range bounds must be finite numbers or None")
                if lower is not None and upper is not None and lower > upper:
                    raise ValueError("Range minimum exceeds maximum")
            default = parameter.default
            if default is not inspect.Parameter.empty and default is not None:
                if allowed is not None and default not in allowed:
                    raise ValueError("Default is outside choices")
                if lower is not None and default < lower or upper is not None and default > upper:
                    raise ValueError("Default is outside range")
            arguments.append(
                {
                    "name": parameter.name,
                    "choices": allowed,
                    "min": lower,
                    "max": upper,
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
            error_messages=messages,
        )
        return callback

    return decorate
