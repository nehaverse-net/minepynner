from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Callable

from ._bridge import owner_context
from .errors import RegistrationError
from .types import wire


@dataclass
class Handler:
    id: str
    owner: str
    callback: Callable[..., Any]
    metadata: dict[str, Any]


@dataclass
class Registry:
    handlers: dict[str, Handler] = field(default_factory=dict)
    weapons: dict[str, dict[str, Any]] = field(default_factory=dict)
    mobs: dict[str, dict[str, Any]] = field(default_factory=dict)
    counter: int = 0

    def add(self, callback: Callable[..., Any], **metadata: Any) -> str:
        self.counter += 1
        ident = f"h{self.counter}"
        self.handlers[ident] = Handler(ident, owner_context.get(), callback, wire(metadata))
        return ident

    def manifest(self) -> dict[str, Any]:
        return {
            "handlers": [
                {"id": h.id, "owner": h.owner, **h.metadata} for h in self.handlers.values()
            ],
            "weapons": self.weapons,
            "mobs": self.mobs,
        }


registry = Registry()


def reset() -> None:
    global registry
    registry = Registry()


def definition_id(value: str) -> str:
    if not re.fullmatch(r"[a-z0-9_]+", value):
        raise RegistrationError(
            "Definition IDs must contain lowercase letters, digits and underscores"
        )
    return value


def register_definition(
    kind: str, ident: str, cls: type, fields: tuple[str, ...], hooks: tuple[str, ...]
) -> type:
    ident = definition_id(ident)
    definitions = getattr(registry, kind + "s")
    if ident in definitions:
        raise RegistrationError(f"Duplicate {kind} ID: {ident}")
    instance = cls()
    definition = {name: wire(getattr(instance, name)) for name in fields if hasattr(instance, name)}
    definition["owner"] = owner_context.get()
    definition["hooks"] = {}
    for hook in hooks:
        callback = getattr(instance, hook, None)
        if callback is not None:
            definition["hooks"][hook] = registry.add(
                callback, kind=kind, definition=ident, hook=hook
            )
    definitions[ident] = definition
    return cls
