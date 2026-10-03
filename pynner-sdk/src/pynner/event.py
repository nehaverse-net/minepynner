from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, ClassVar, TypeVar

from . import _registry
from .entity import Entity
from .player import Player
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


class PlayerJoinEvent(Event):
    event_name = "player_join"
    player: Player


class PlayerQuitEvent(Event):
    event_name = "player_quit"
    player: Player


class PlayerMoveEvent(Event):
    event_name = "player_move"
    player: Player


class PlayerInteractEvent(Event):
    event_name = "player_interact"
    player: Player


class EntityDamageEvent(Event):
    event_name = "entity_damage"


class EntityDamageByEntityEvent(Event):
    event_name = "entity_damage_by_entity"


class EntityDeathEvent(Event):
    event_name = "entity_death"


class EntitySpawnEvent(Event):
    event_name = "entity_spawn"


class BlockBreakEvent(Event):
    event_name = "block_break"


class BlockPlaceEvent(Event):
    event_name = "block_place"


class InventoryClickEvent(Event):
    event_name = "inventory_click"


class ProjectileHitEvent(Event):
    event_name = "projectile_hit"


class AsyncChatEvent(Event):
    event_name = "async_chat"


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
