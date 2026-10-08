"""Start isolated Paper and exercise the weather GUI and real chest sorting."""

import json
import subprocess
from pathlib import Path

from pynner_debug.runner import CONFIG, Session

ROOT = Path(__file__).resolve().parents[1]


def main():
    settings = json.loads(CONFIG.read_text(encoding="utf-8"))
    settings["plugin_jar"] = str(ROOT / "dist/pynner-paper-0.1.0.jar")
    settings["sessions"] = str(ROOT / ".debug-smoke/gui")
    probe = ROOT / ".debug-smoke/gui-probe.py"
    probe.parent.mkdir(exist_ok=True)
    probe.write_text(
        (ROOT / "examples/weather_chest.py").read_text(encoding="utf-8")
        + "\n" + (ROOT / "examples/weather_menu_pages.py").read_text(encoding="utf-8")
        + '\nfrom pynner.errors import BridgeError\n'
        + 'views = {}\n'
        + '@event(InventoryClickEvent, include_cancelled=True)\n'
        + 'def remember(e):\n'
        + '    if e.player is not None: views[e.player.uuid] = e.data["view_id"]\n'
        + 'old_views = {}\n'
        + '@event(InventoryClickEvent, include_cancelled=True)\n'
        + 'def remember_old(e):\n'
        + '    if e.player is not None and e.gui_id == "weather_pages": old_views[e.player.uuid] = e.view_id\n'
        + '@command("guipatchprobe", player_only=True)\n'
        + 'async def gui_patch(ctx):\n'
        + '    view = views[ctx.player.uuid]\n'
        + '    try: await ctx.player.update_gui(view, {0: "STONE", 9: "STONE"})\n'
        + '    except BridgeError: await ctx.reply("GUI_ATOMIC_REJECTED")\n'
        + '    await ctx.player.update_gui(view, {2: None, 4: "STONE"})\n'
        + '@command("guirestoreprobe", player_only=True)\n'
        + 'async def gui_restore(ctx):\n'
        + '    await ctx.player.update_gui(views[ctx.player.uuid], page_items(1), clear=True)\n'
        + '@command("guistaleprobe", player_only=True)\n'
        + 'async def gui_stale(ctx):\n'
        + '    try: await ctx.player.update_gui(old_views[ctx.player.uuid], {2: "STONE"})\n'
        + '    except BridgeError: await ctx.reply("GUI_STALE_REJECTED")\n'
        + '@command("gridprobe", player_only=True)\n'
        + 'async def grid_probe(ctx):\n'
        + '    grid = [[None] * 9 for _ in range(3)]\n'
        + '    grid[1][4], grid[2][8] = "DIAMOND", "BARRIER"\n'
        + '    await ctx.player.open_gui("grid_probe", "Grid", grid)\n'
        + '@event(InventoryClickEvent, include_cancelled=True)\n'
        + 'def grid_coordinates(e):\n'
        + '    if e.gui_id == "grid_probe" and e.row == 1 and e.column == 4: e.player.send_message("GRID_COORDINATES_OK")\n'
        + '@command("sortprobe", player_only=True)\n'
        + 'async def probe_sort(ctx):\n'
        + '    try: await ctx.player.sort_inventory(views[ctx.player.uuid])\n'
        + '    except BridgeError: await ctx.reply("SORT_REJECTED")\n',
        encoding="utf-8",
    )
    session = Session(None, probe, settings)
    try:
        session.prepare("GuiTester")
        session.start()
        if not session.ready.wait(120) or not session.active.wait(15):
            raise RuntimeError(f"Startup failed: {session.directory / 'launcher.log'}")
        subprocess.run(
            ["node", str(ROOT / "tools/client/gui.cjs"), str(session.port)],
            check=True,
            timeout=60,
        )
        print(f"GUI and sorting verified: {session.directory}")
    finally:
        session.close()


if __name__ == "__main__":
    main()
