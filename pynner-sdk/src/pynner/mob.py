from ._bridge import OperationReceipt, request
from ._registry import register_definition
from .entity import Entity
from .types import Location

FIELDS = (
    "base",
    "name",
    "health",
    "attack_damage",
    "armor",
    "movement_speed",
    "ai",
    "equipment",
    "drops",
    "experience",
    "size",
    "attributes",
    "pdc",
    "tick_seconds",
)
HOOKS = (
    "on_spawn",
    "on_tick",
    "on_attack",
    "on_damage",
    "on_death",
    "on_target",
    "on_move",
    "on_interact",
)


def mob(ident: str):
    def decorate(cls: type) -> type:
        return register_definition("mob", ident, cls, FIELDS, HOOKS)

    return decorate


def spawn_mob(ident: str, location: Location) -> OperationReceipt[Entity]:
    return request("world.spawn_mob", id=ident, location=location).map(Entity)
