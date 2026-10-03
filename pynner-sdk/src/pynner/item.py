from dataclasses import dataclass, field

from .types import Material


@dataclass(frozen=True)
class ItemSnapshot:
    material: str
    amount: int
    name: str = ""
    weapon_id: str | None = None
    damage: int = 0
    pdc: dict[str, str | int | float] = field(default_factory=dict)


@dataclass(frozen=True)
class AttributeModifier:
    attribute: str
    amount: float
    operation: str = "ADD_NUMBER"
    slot: str = "MAINHAND"


@dataclass(frozen=True)
class Recipe:
    shape: tuple[str, ...]
    ingredients: dict[str, str]


@dataclass(frozen=True)
class ItemSpec:
    material: Material | str = Material.STICK
    name: str = ""
    lore: tuple[str, ...] = ()
    custom_model_data: int | None = None
    durability: int | None = None
    enchantments: dict[str, int] = field(default_factory=dict)
    attributes: tuple[AttributeModifier, ...] = ()
    pdc: dict[str, str | int | float] = field(default_factory=dict)
