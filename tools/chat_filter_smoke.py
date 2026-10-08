"""Probe real Paper chat filtering and delivery to a dead player."""
import json
import subprocess
from pathlib import Path

from pynner_debug.runner import CONFIG, Session

ROOT = Path(__file__).resolve().parents[1]


def main():
    folder = ROOT / ".debug-smoke" / "chat-filter"
    folder.mkdir(parents=True, exist_ok=True)
    source = folder / "chat.py"
    source.write_text(
        'from pynner import AsyncChatEvent, EntityDeathEvent, event\n'
        '@event(AsyncChatEvent, cancel=True, message_contains=["イキスギ", "お前やりませんねぇすぎぃ"])\n'
        'def blocked(e):\n'
        '    e.player.send_message("CHAT_WARNING")\n'
        '@event(EntityDeathEvent, entity_type="PLAYER")\n'
        'async def death(e):\n'
        '    if e.player is not None:\n'
        '        await e.player.send_message("DEATH_NOTICE")\n',
        encoding="utf-8",
    )
    settings = json.loads(CONFIG.read_text(encoding="utf-8"))
    settings["plugin_jar"] = str(ROOT / "dist" / "pynner-paper-0.1.0.jar")
    settings["sessions"] = str(folder / "sessions")
    session = Session(None, source, settings)
    try:
        session.prepare("ChatSender")
        properties = session.directory / "server.properties"
        properties.write_text(
            properties.read_text(encoding="utf-8").replace("max-players=1", "max-players=2"),
            encoding="utf-8",
        )
        session.start()
        if not session.ready.wait(120) or not session.active.wait(15):
            raise RuntimeError(f"Paper failed to start: {session.directory / 'launcher.log'}")
        subprocess.run(
            ["node", str(ROOT / "tools" / "client" / "chat-filter.cjs"), str(session.port)],
            check=True, timeout=40,
        )
        print(f"Paper chat filter verified: {session.directory}")
    finally:
        session.close()


if __name__ == "__main__":
    main()
