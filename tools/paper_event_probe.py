"""Actual-server event probes for the isolated verification environment."""

from pynner import Entity, Event, Material, event, mob, weapon


def observe(e: Event) -> None:
    if e.data:
        print("PYNNER_EVENT:" + e.data["event"])


for name in (
    "player_join",
    "player_quit",
    "player_move",
    "player_interact",
    "entity_damage",
    "entity_damage_by_entity",
    "entity_death",
    "entity_spawn",
    "block_break",
    "block_place",
    "inventory_click",
    "projectile_hit",
    "async_chat",
):
    event(name, include_cancelled=True, rate_limit=2)(observe)


@weapon("smoke_sword")
class SmokeSword:
    material = Material.IRON_SWORD
    damage = 50
    uses = 2
    cooldown = 0.2

    def on_left_click(self, e: Event):
        print("PYNNER_WEAPON:on_left_click")

    def on_right_click(self, e: Event):
        print("PYNNER_WEAPON:on_right_click")

    def on_hit(self, e: Event):
        print("PYNNER_WEAPON:on_hit")

    def on_kill(self, e: Event):
        print("PYNNER_WEAPON:on_kill")

    def on_damage(self, e: Event):
        print("PYNNER_WEAPON:on_damage")

    def on_break(self, e: Event):
        print("PYNNER_WEAPON:on_break")

    def on_equip(self, e: Event):
        print("PYNNER_WEAPON:on_equip")

    def on_unequip(self, e: Event):
        print("PYNNER_WEAPON:on_unequip")


@mob("probe_mob")
class ProbeMob:
    base = "ZOMBIE"
    health = 5
    ai = False

    def on_spawn(self, e: Entity):
        print("PYNNER_MOB:on_spawn")

    def on_tick(self, e: Entity):
        pass

    def on_damage(self, e: Event):
        print("PYNNER_MOB:on_damage")

    def on_death(self, e: Event):
        print("PYNNER_MOB:on_death")

    def on_interact(self, e: Event):
        print("PYNNER_MOB:on_interact")
