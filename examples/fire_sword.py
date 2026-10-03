from pynner import AttributeModifier, Material, Recipe, WeaponHitEvent, weapon


@weapon("fire_sword")
class FireSword:
    name = "§c炎の剣"
    material = Material.DIAMOND_SWORD
    lore = ("Python製の武器",)
    custom_model_data = 1001
    damage = 12
    attack_speed = 1.6
    durability = 500
    cooldown = 0.5
    uses = 100
    enchantments = {"minecraft:unbreaking": 2}
    attributes = (AttributeModifier("minecraft:luck", 1),)
    pdc = {"example:element": "fire"}
    recipe = Recipe((" D ", " D ", " S "), {"D": "DIAMOND", "S": "STICK"})

    def on_hit(self, e: WeaponHitEvent) -> None:
        if e.target is not None:
            e.target.set_fire(5)
