from __future__ import annotations

from ._bridge import OperationReceipt
from .entity import Entity
from .item import ItemSnapshot, ItemSpec


class Inventory:
    def __init__(self, player: Player):
        self.player = player

    @property
    def contents(self) -> tuple[ItemSnapshot | None, ...]:
        return tuple(
            ItemSnapshot(**item) if item else None
            for item in self.player._snapshot.get("inventory", [])
        )

    def give(self, item: ItemSpec | str, amount: int = 1) -> OperationReceipt:
        return self.player.give_item(item, amount)

    def remove(self, item: ItemSpec | str, amount: int = 1) -> OperationReceipt:
        return self.player.remove_item(item, amount)


class Player(Entity):
    def send_message(self, message: str) -> OperationReceipt:
        return self._call("player.send_message", message=message)

    def send_actionbar(self, message: str) -> OperationReceipt:
        return self._call("player.send_actionbar", message=message)

    def send_title(self, title: str, subtitle: str = "") -> OperationReceipt:
        return self._call("player.send_title", title=title, subtitle=subtitle)

    def give_item(self, item: ItemSpec | str, amount: int = 1) -> OperationReceipt:
        return self._call("player.give_item", item=item, amount=amount)

    def remove_item(self, item: ItemSpec | str, amount: int = 1) -> OperationReceipt:
        return self._call("player.remove_item", item=item, amount=amount)

    @property
    def food(self) -> int:
        return int(self._snapshot.get("food", 20))

    @food.setter
    def food(self, value: int) -> None:
        self._call("player.set_food", value=value)

    @property
    def level(self) -> int:
        return int(self._snapshot.get("level", 0))

    @level.setter
    def level(self, value: int) -> None:
        self._call("player.set_level", value=value)

    @property
    def inventory(self) -> Inventory:
        return Inventory(self)
