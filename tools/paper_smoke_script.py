"""Copy into the isolated smoke server; do not install in production."""

from pynner import CommandContext, Entity, EntityType, Location, command, mob, server


@mob("smoke_mob")
class SmokeMob:
    base = EntityType.ZOMBIE
    health = 40
    armor = 4
    ai = False
    size = 1.1
    experience = 7
    drops = [{"item": "DIAMOND", "amount": 1}]


@command("pynnersmoke")
async def smoke(ctx: CommandContext) -> None:
    from pynner import spawn_mob

    entity = await spawn_mob("smoke_mob", Location("world", 0, -58, 0))
    assert entity.health == 40
    await entity.damage(5)
    await entity.refresh()
    assert 0 < entity.health < 40
    await entity.heal(5)
    await entity.set_fire(3)
    await entity.teleport(Location("world", 2, -58, 0))
    await entity.refresh()
    assert entity.location.x == 2
    await entity.set_pdc("smoke:verified", "yes")
    await entity.add_effect("minecraft:speed", 2, 1)
    await entity.remove_effect("minecraft:speed")
    await entity.kill()
    status = await server.status()
    assert status["active"]
    await ctx.reply("PYNNER_SMOKE_OK")


@command("pynnerconsole", aliases=["pyconsole"])
async def console(ctx: CommandContext, amount: int, enabled: bool = True) -> None:
    await ctx.reply(f"PYNNER_CONSOLE_OK:{amount}:{enabled}")


def on_load() -> None:
    print("PYNNER_ON_LOAD")


def on_enable() -> None:
    print("PYNNER_ON_ENABLE")


def on_disable() -> None:
    print("PYNNER_ON_DISABLE")


@command("pynnerfail")
def fail(ctx: CommandContext) -> None:
    raise ValueError("PYNNER_EXPECTED_FAILURE")


@command("pynnercrash")
def crash(ctx: CommandContext) -> None:
    import os
    os._exit(13)
