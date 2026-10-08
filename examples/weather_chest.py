"""/cher opens a weather menu; empty slots in ordinary chests trigger sorting."""

from pynner import CommandContext, InventoryClickEvent, ItemSpec, command, event


@command("cher", player_only=True, permission="pynner.admin")
async def weather_menu(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    await ctx.player.open_gui(
        "weather_menu",
        "天候を選択",
        [
            [
                None,
                None,
                ItemSpec(material="SUNFLOWER", name="§e晴れ", lore=("クリックで晴れにします",)),
                None,
                None,
                None,
                ItemSpec(material="WATER_BUCKET", name="§b雨", lore=("クリックで雨にします",)),
                None,
                None,
            ]
        ],
    )


@event(InventoryClickEvent, include_cancelled=True)
async def choose_weather(e: InventoryClickEvent) -> None:
    if e.player is None or e.gui_id != "weather_menu" or not e.in_top or e.click != "LEFT":
        return
    weather = {2: "clear", 6: "rain"}.get(e.slot)
    if weather is None:
        return
    await e.player.world.set_weather(weather, seconds=600)
    await e.player.close_inventory(e.view_id)
    await e.player.send_message("晴れにしました。" if weather == "clear" else "雨にしました。")


@event(InventoryClickEvent)
async def sort_chest(e: InventoryClickEvent) -> None:
    if (
        e.player is None
        or e.cancelled
        or e.gui_id
        or e.inventory_type != "CHEST"
        or not e.in_top
        or not e.empty
        or not e.cursor_empty
        or e.click != "LEFT"
    ):
        return
    await e.player.sort_inventory(e.view_id)
    await e.player.send_message("チェストを整頓しました。")


if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
