from pynner import Entity, EntityType, Event, ItemSpec, Material, mob


@mob("boss_zombie")
class BossZombie:
    base = EntityType.ZOMBIE
    name = "§cBoss Zombie"
    health = 500
    attack_damage = 15
    armor = 8
    movement_speed = 0.25
    size = 1.2
    ai = True
    equipment = {"HAND": ItemSpec(Material.IRON_SWORD)}
    drops = [{"item": "DIAMOND", "amount": 3, "chance": 1.0}]
    experience = 50
    tick_seconds = 1

    def on_spawn(self, entity: Entity) -> None:
        entity.name = "§cBoss Zombie"

    def on_attack(self, e: Event) -> None:
        if e.target is not None:
            e.target.set_fire(3)

    def on_death(self, e: Event) -> None:
        if e.world is not None:
            e.world.broadcast("Boss defeated!")
