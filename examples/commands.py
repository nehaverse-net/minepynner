from pynner import CommandContext, Player, command, scheduler, server


@command("heal", aliases=["pyheal"], permission="pynner.example.heal", player_only=True)
async def heal(ctx: CommandContext) -> None:
    if ctx.player is not None:
        await ctx.player.set_health(20)
        await ctx.reply("Healed!")


@command("givecoin", permission="pynner.example.coin")
def give_coin(ctx: CommandContext, player: Player, amount: int) -> None:
    if not 1 <= amount <= 64:
        ctx.reply("amount must be 1..64")
        return
    player.give_item("GOLD_INGOT", amount)
    ctx.reply("Coins given")


@scheduler.delay(seconds=5)
def announce() -> None:
    server.broadcast("Python scripts enabled")
