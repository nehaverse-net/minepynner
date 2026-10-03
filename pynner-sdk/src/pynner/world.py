from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._bridge import OperationReceipt, request
from .types import Location

if TYPE_CHECKING:
    from .entity import Entity


@dataclass(frozen=True)
class World:
    name: str

    def broadcast(self, message: str) -> OperationReceipt:
        return request("world.broadcast", world=self.name, message=message)

    def spawn_mob(self, ident: str, location: Location) -> OperationReceipt["Entity"]:
        from .mob import spawn_mob

        return spawn_mob(ident, location)

    def set_block(self, location: Location, material: str) -> OperationReceipt:
        return request("world.set_block", location=location, material=material)
