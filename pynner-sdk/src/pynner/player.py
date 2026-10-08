from __future__ import annotations

from collections.abc import Mapping, Sequence

from ._bridge import OperationReceipt
from .entity import Entity
from .item import ItemSnapshot, ItemSpec
from .types import wire

GuiLayout = Mapping[int, ItemSpec | str | None] | Sequence[Sequence[ItemSpec | str | None]]


def _gui_layout(items: GuiLayout, size: int | None) -> tuple[int, dict[str, object]]:
    if isinstance(items, Mapping):
        actual_size = 9 if size is None else size
        slots = dict(items)
    else:
        if (
            isinstance(items, (str, bytes))
            or not isinstance(items, Sequence)
            or not 1 <= len(items) <= 6
        ):
            raise ValueError("GUI layout must contain 1 to 6 rows of 9 cells")
        if any(
            isinstance(row, (str, bytes)) or not isinstance(row, Sequence) or len(row) != 9
            for row in items
        ):
            raise ValueError("Each GUI row must contain exactly 9 cells")
        actual_size = len(items) * 9
        if size is not None and size != actual_size:
            raise ValueError("GUI size must match the number of layout rows")
        slots = {
            row * 9 + column: item
            for row, cells in enumerate(items)
            for column, item in enumerate(cells)
        }
    if type(actual_size) is not int or actual_size not in {9, 18, 27, 36, 45, 54}:
        raise ValueError("GUI size must be a multiple of 9 between 9 and 54")
    if any(type(slot) is not int or not 0 <= slot < actual_size for slot in slots):
        raise ValueError("GUI slots must be integers inside the inventory")
    if any(item is not None and not isinstance(item, (ItemSpec, str)) for item in slots.values()):
        raise ValueError("GUI cells must be ItemSpec, material strings, or None")
    return actual_size, {str(slot): wire(item) for slot, item in slots.items()}


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

    def open_gui(
        self, gui_id: str, title: str, items: GuiLayout, size: int | None = None
    ) -> OperationReceipt:
        """Open a menu using rows of 9 cells or slot numbers. Row layouts infer size."""
        actual_size, slots = _gui_layout(items, size)
        if not gui_id or not gui_id.strip():
            raise ValueError("GUI ID must not be blank")
        return self._call(
            "player.open_gui",
            gui_id=gui_id,
            title=title,
            size=actual_size,
            items={slot: item for slot, item in slots.items() if item is not None},
        )

    def update_gui(
        self, view_id: str, items: GuiLayout, *, clear: bool = False
    ) -> OperationReceipt:
        """Patch menu slots or rows; None removes an item. clear replaces all contents."""
        _, slots = _gui_layout(items, 54 if isinstance(items, Mapping) else None)
        return self._call("player.update_gui", view_id=view_id, clear=clear, items=slots)

    def switch_gui(
        self,
        view_id: str,
        gui_id: str,
        title: str,
        items: GuiLayout,
        size: int | None = None,
    ) -> OperationReceipt:
        """Replace the current menu only if its view ID still matches; return the new ID."""
        actual_size, slots = _gui_layout(items, size)
        if not gui_id or not gui_id.strip():
            raise ValueError("GUI ID must not be blank")
        return self._call(
            "player.switch_gui",
            view_id=view_id,
            gui_id=gui_id,
            title=title,
            size=actual_size,
            items={slot: item for slot, item in slots.items() if item is not None},
        )

    def close_inventory(self, view_id: str) -> OperationReceipt:
        return self._call("player.close_inventory", view_id=view_id)

    def sort_inventory(self, view_id: str) -> OperationReceipt:
        """Sort the currently open ordinary chest, with an empty cursor and matching view ID."""
        return self._call("player.sort_inventory", view_id=view_id)
