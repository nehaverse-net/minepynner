"""Run this file with Python while Minecraft with Pynner Debug is on its title screen."""
from pynner import (
    CommandContext,
    EntityType,
    Event,
    Material,
    PlayerJoinEvent,
    command,
    event,
    mob,
    server,
    weapon,
)


@weapon("debug_blade")
class DebugBlade:
    material = Material.DIAMOND_SWORD
    name = "§bDebug Blade"
    damage = 12

    def on_hit(self, e: Event) -> None:
        if e.target is not None:
            e.target.set_fire(3)


@mob("debug_target")
class DebugTarget:
    base = EntityType.ZOMBIE
    name = "Debug Target"
    health = 40
    ai = False


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("Pynner Debug ready! /pynner give debug_blade /pynner spawn debug_target")


@command("debugerror")
def deliberate_error(ctx: CommandContext) -> None:
    raise ValueError("Intentional error for the Fabric overlay")


def on_enable() -> None:
    server.broadcast("Pynner Python debug script enabled")


if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
