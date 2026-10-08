"""/cherpages demonstrates menu updates and confirmation; /pynner_weather has constraints."""

from pynner import (
    CommandContext,
    InventoryClickEvent,
    ItemSpec,
    PlayerQuitEvent,
    command,
    event,
)

OPTIONS = [
    ("clear", "晴れ", "SUNFLOWER"),
    ("rain", "雨", "WATER_BUCKET"),
    ("thunder", "雷雨", "LIGHTNING_ROD"),
]
# Player UUID -> (current view ID, selected option). Each player has their own selection.
selections: dict[str, tuple[str, int]] = {}


def page_items(index: int) -> list[list[ItemSpec | str | None]]:
    _, label, material = OPTIONS[index]
    return [
        [
            None,
            None,
            ItemSpec(material, name=f"§e{label}"),
            None,
            None,
            None,
            ItemSpec("LIME_WOOL", name="§aこの天候を選ぶ"),
            None,
            ItemSpec("ARROW", name="§f次の天候"),
        ]
    ]


@command("cherpages", player_only=True, permission="pynner.admin")
async def open_pages(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    view_id = await ctx.player.open_gui("weather_pages", "天候を選択", page_items(0))
    selections[ctx.player.uuid] = (view_id, 0)


@event(InventoryClickEvent, include_cancelled=True)
async def menu_click(e: InventoryClickEvent) -> None:
    if e.player is None or not e.in_top or e.click != "LEFT":
        return
    selected = selections.get(e.player.uuid)
    if selected is None or selected[0] != e.view_id:
        return
    _, index = selected
    if e.gui_id == "weather_pages":
        if e.slot == 8:
            index = (index + 1) % len(OPTIONS)
            await e.player.update_gui(e.view_id, page_items(index), clear=True)
            selections[e.player.uuid] = (e.view_id, index)
        elif e.slot == 6:
            new_id = await e.player.switch_gui(
                e.view_id,
                "weather_confirm",
                f"{OPTIONS[index][1]}にしますか？",
                {
                    2: ItemSpec("LIME_WOOL", name="§aはい"),
                    6: ItemSpec("RED_WOOL", name="§cキャンセル"),
                },
            )
            selections[e.player.uuid] = (new_id, index)
    elif e.gui_id == "weather_confirm" and e.slot in (2, 6):
        if e.slot == 2:
            await e.player.world.set_weather(OPTIONS[index][0])
            await e.player.send_message(f"{OPTIONS[index][1]}にしました。")
        await e.player.close_inventory(e.view_id)
        selections.pop(e.player.uuid, None)


@event(PlayerQuitEvent)
def forget_selection(e: PlayerQuitEvent) -> None:
    selections.pop(e.player.uuid, None)


@command(
    "pynner_weather",
    player_only=True,
    permission="pynner.admin",
    choices={"mode": ["clear", "rain", "thunder"]},
    ranges={"seconds": (1, 86400)},
    error_messages={
        "mode.missing": "使い方: /pynner_weather <clear|rain|thunder> [秒数]",
        "mode.choices": "天候は {choices} から選んでください。",
        "seconds.invalid": "秒数は整数で入力してください。",
        "seconds.range": "秒数は {min}〜{max} にしてください。",
        "too_many": "引数が多すぎます。天候と秒数を指定してください。",
        "permission": "天候を変える権限がありません。",
    },
)
async def weather_command(ctx: CommandContext, mode: str, seconds: int = 600) -> None:
    if ctx.player is None:
        return
    await ctx.player.world.set_weather(mode, seconds)
    await ctx.reply(f"天候を {mode} に変更しました（{seconds}秒）。")


if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
