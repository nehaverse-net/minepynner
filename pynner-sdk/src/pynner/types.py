from __future__ import annotations

from dataclasses import asdict, dataclass, is_dataclass
from enum import StrEnum
from typing import Any, TypedDict

from .constants import EntityType as EntityType
from .constants import Material as Material


@dataclass(frozen=True)
class Vector:
    x: float = 0
    y: float = 0
    z: float = 0


@dataclass(frozen=True)
class Location:
    world: str
    x: float
    y: float
    z: float
    yaw: float = 0
    pitch: float = 0


class Envelope(TypedDict):
    version: int
    type: str
    generation: int
    payload: dict[str, Any]


def wire(value: Any) -> Any:
    if hasattr(value, "_reference"):
        return value._reference()
    if is_dataclass(value) and not isinstance(value, type):
        return wire(asdict(value))
    if isinstance(value, dict):
        return {str(k): wire(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [wire(v) for v in value]
    if isinstance(value, StrEnum):
        return str(value)
    return value
