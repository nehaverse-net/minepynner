from ._registry import register_definition

FIELDS = (
    "name",
    "material",
    "lore",
    "custom_model_data",
    "damage",
    "attack_speed",
    "durability",
    "enchantments",
    "attributes",
    "cooldown",
    "uses",
    "pdc",
    "recipe",
)
HOOKS = (
    "on_left_click",
    "on_right_click",
    "on_hit",
    "on_kill",
    "on_damage",
    "on_break",
    "on_equip",
    "on_unequip",
)


def weapon(ident: str):
    def decorate(cls: type) -> type:
        return register_definition("weapon", ident, cls, FIELDS, HOOKS)

    return decorate
