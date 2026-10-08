from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Any, ClassVar, TypeVar

from . import _registry
from .entity import Entity
from .item import ItemSnapshot
from .player import Player
from .types import Location
from .world import World

F = TypeVar("F", bound=Callable[..., Any])


@dataclass(frozen=True)
class Event:
    event_name: ClassVar[str] = ""
    cancelled: bool = False
    player: Player | None = None
    entity: Entity | None = None
    target: Entity | None = None
    attacker: Entity | None = None
    world: World | None = None
    damage: float = 0
    data: dict[str, Any] | None = None

    @property
    def entity_type(self) -> str:
        snapshot = (self.data or {}).get("entity") or (self.data or {}).get("player") or {}
        return str(snapshot.get("type", ""))


class PlayerJoinEvent(Event):
    event_name = "player_join"
    player: Player


class PlayerQuitEvent(Event):
    event_name = "player_quit"
    player: Player


class PlayerMoveEvent(Event):
    event_name = "player_move"

    @property
    def from_location(self) -> Location | None:
        value = (self.data or {}).get("from")
        return Location(**value) if value else None

    @property
    def to_location(self) -> Location | None:
        value = (self.data or {}).get("to")
        return Location(**value) if value else None

    player: Player


class PlayerInteractEvent(Event):
    event_name = "player_interact"

    @property
    def action(self) -> str:
        return str((self.data or {}).get("action", ""))

    @property
    def hand(self) -> str:
        return str((self.data or {}).get("hand", ""))

    @property
    def material(self) -> str:
        return str((self.data or {}).get("material", ""))

    player: Player


class EntityDamageEvent(Event):
    event_name = "entity_damage"

    @property
    def cause(self) -> str:
        return str((self.data or {}).get("cause", ""))


class EntityDamageByEntityEvent(Event):
    event_name = "entity_damage_by_entity"


class EntityDeathEvent(Event):
    event_name = "entity_death"

    @property
    def experience(self) -> int:
        return int((self.data or {}).get("experience", 0))


class EntitySpawnEvent(Event):
    event_name = "entity_spawn"


class BlockBreakEvent(Event):
    event_name = "block_break"

    @property
    def material(self) -> str:
        return str((self.data or {}).get("material", ""))

    @property
    def block(self) -> Location | None:
        value = (self.data or {}).get("block")
        return Location(**value) if value else None


class BlockPlaceEvent(Event):
    event_name = "block_place"

    @property
    def material(self) -> str:
        return str((self.data or {}).get("material", ""))

    @property
    def block(self) -> Location | None:
        value = (self.data or {}).get("block")
        return Location(**value) if value else None


class InventoryClickEvent(Event):
    event_name = "inventory_click"

    @property
    def slot(self) -> int:
        return int((self.data or {}).get("slot", -1))

    @property
    def row(self) -> int | None:
        return self.slot // 9 if self.in_top and self.slot >= 0 else None

    @property
    def column(self) -> int | None:
        return self.slot % 9 if self.in_top and self.slot >= 0 else None

    @property
    def click(self) -> str:
        return str((self.data or {}).get("click", ""))

    @property
    def view_id(self) -> str:
        return str((self.data or {}).get("view_id", ""))

    @property
    def gui_id(self) -> str:
        return str((self.data or {}).get("gui_id", ""))

    @property
    def inventory_type(self) -> str:
        return str((self.data or {}).get("inventory_type", ""))

    @property
    def top_size(self) -> int:
        return int((self.data or {}).get("top_size", 0))

    @property
    def in_top(self) -> bool:
        return bool((self.data or {}).get("in_top", False))

    @property
    def empty(self) -> bool:
        return bool((self.data or {}).get("empty", True))

    @property
    def cursor_empty(self) -> bool:
        return bool((self.data or {}).get("cursor_empty", True))

    @property
    def item(self) -> ItemSnapshot | None:
        value = (self.data or {}).get("item")
        return ItemSnapshot(**value) if value else None


class ProjectileHitEvent(Event):
    event_name = "projectile_hit"

    @property
    def block(self) -> Location | None:
        value = (self.data or {}).get("block")
        return Location(**value) if value else None


class AsyncChatEvent(Event):
    event_name = "async_chat"

    @property
    def message(self) -> str:
        return str((self.data or {}).get("message", ""))


class WeaponHitEvent(Event):
    pass


class MobEvent(Event):
    pass


EVENT_TYPES = {
    cls.event_name: cls
    for cls in (
        PlayerJoinEvent,
        PlayerQuitEvent,
        PlayerMoveEvent,
        PlayerInteractEvent,
        EntityDamageEvent,
        EntityDamageByEntityEvent,
        EntityDeathEvent,
        EntitySpawnEvent,
        BlockBreakEvent,
        BlockPlaceEvent,
        InventoryClickEvent,
        ProjectileHitEvent,
        AsyncChatEvent,
    )
}


def event(
    kind: str | type[Event],
    *,
    world: str | None = None,
    entity_type: str | None = None,
    min_distance: float = 0,
    rate_limit: float = 0,
    include_cancelled: bool = False,
    cancel: bool = False,
    material: str | None = None,
    permission: str | None = None,
    message_contains: str | Sequence[str] | None = None,
) -> Callable[[F], F]:
    """cancel=True installs a Java-side rule; callback events are read snapshots."""
    name = kind if isinstance(kind, str) else kind.event_name
    if name not in EVENT_TYPES:
        raise ValueError(f"Unknown event: {name}")
    if cancel and name not in {
        "player_move",
        "player_interact",
        "entity_damage",
        "entity_damage_by_entity",
        "block_break",
        "block_place",
        "inventory_click",
        "projectile_hit",
        "async_chat",
    }:
        raise ValueError(f"Event is not cancellable: {name}")
    if min_distance < 0 or rate_limit < 0:
        raise ValueError("Event limits must be non-negative")
    chat_words: list[str] | None = None
    if message_contains is not None:
        if name != "async_chat":
            raise ValueError("message_contains is only supported for async_chat")
        if isinstance(message_contains, str):
            chat_words = [message_contains]
        elif isinstance(message_contains, Sequence):
            chat_words = list(message_contains)
        else:
            raise ValueError("message_contains must be a string or sequence of strings")
        if not chat_words or any(not isinstance(word, str) or not word for word in chat_words):
            raise ValueError("message_contains requires at least one non-empty string")

    def decorate(callback: F) -> F:
        _registry.registry.add(
            callback,
            kind="event",
            event=name,
            world=world,
            entity_type=entity_type,
            min_distance=min_distance,
            rate_limit=rate_limit,
            include_cancelled=include_cancelled,
            cancel=cancel,
            material=material,
            permission=permission,
            message_contains=chat_words,
        )
        return callback

    return decorate


def decode_event(payload: dict[str, Any]) -> Event:
    fields: dict[str, Any] = {
        "cancelled": payload.get("cancelled", False),
        "damage": payload.get("damage", 0),
        "data": payload,
    }
    for key in ("player", "entity", "target", "attacker"):
        if payload.get(key):
            snapshot = payload[key]
            fields[key] = Player(snapshot) if snapshot.get("type") == "PLAYER" else Entity(snapshot)
    if payload.get("world"):
        fields["world"] = World(payload["world"])
    return EVENT_TYPES.get(payload.get("event", ""), Event)(**fields)
