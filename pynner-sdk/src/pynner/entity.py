from __future__ import annotations

from typing import Any

from ._bridge import OperationReceipt, request
from .types import Location, Vector
from .world import World


class Entity:
    """An entity reference with a read snapshot and queued mutation methods."""

    def __init__(self, snapshot: dict[str, Any]):
        self._snapshot = dict(snapshot)
        self.uuid: str = snapshot["uuid"]

    def _reference(self) -> dict[str, Any]:
        return {"uuid": self.uuid}

    def _call(self, operation: str, **arguments: Any) -> OperationReceipt:
        return request(operation, self._reference(), **arguments)

    @property
    def health(self) -> float:
        return float(self._snapshot.get("health", 0))

    @health.setter
    def health(self, value: float) -> None:
        self.set_health(value)

    def set_health(self, value: float) -> OperationReceipt:
        return self._call("entity.set_health", value=value)

    @property
    def name(self) -> str:
        return str(self._snapshot.get("name", ""))

    @name.setter
    def name(self, value: str) -> None:
        self._call("entity.set_name", value=value)

    @property
    def location(self) -> Location:
        return Location(**self._snapshot["location"])

    @property
    def velocity(self) -> Vector:
        return Vector(**self._snapshot.get("velocity", {}))

    @property
    def pdc(self) -> dict[str, str | int | float]:
        return dict(self._snapshot.get("pdc", {}))

    @property
    def max_health(self) -> float:
        return float(self._snapshot.get("max_health", 0))

    @property
    def world(self) -> World:
        return World(self.location.world)

    async def refresh(self) -> Entity:
        self._snapshot = await self._call("entity.snapshot")
        return self

    def teleport(self, location: Location) -> OperationReceipt:
        return self._call("entity.teleport", location=location)

    def kill(self) -> OperationReceipt:
        return self._call("entity.kill")

    def damage(self, amount: float) -> OperationReceipt:
        return self._call("entity.damage", amount=amount)

    def heal(self, amount: float) -> OperationReceipt:
        return self._call("entity.heal", amount=amount)

    def set_velocity(self, velocity: Vector) -> OperationReceipt:
        return self._call("entity.set_velocity", velocity=velocity)

    def add_effect(self, effect: str, seconds: float, amplifier: int = 0) -> OperationReceipt:
        return self._call("entity.add_effect", effect=effect, seconds=seconds, amplifier=amplifier)

    def remove_effect(self, effect: str) -> OperationReceipt:
        return self._call("entity.remove_effect", effect=effect)

    def set_fire(self, seconds: float) -> OperationReceipt:
        return self._call("entity.set_fire", seconds=seconds)

    def lightning(self, damage: bool = False) -> OperationReceipt:
        return self._call("entity.lightning", damage=damage)

    def spawn_particle(self, particle: str, count: int = 10) -> OperationReceipt:
        return self._call("entity.spawn_particle", particle=particle, count=count)

    def play_sound(self, sound: str, volume: float = 1, pitch: float = 1) -> OperationReceipt:
        return self._call("entity.play_sound", sound=sound, volume=volume, pitch=pitch)

    def set_pdc(self, key: str, value: str | int | float) -> OperationReceipt:
        return self._call("entity.set_pdc", key=key, value=value)
