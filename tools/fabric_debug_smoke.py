"""Integration check against an already-running development Fabric client on its title screen."""
import json
import shutil
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import ProxyHandler, build_opener

from pynner_debug.client import DebugError, discover
from pynner_debug.runner import CONFIG, Session


def wait(condition, description, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if condition():
            return
        time.sleep(0.5)
    raise AssertionError(f"Timed out: {description}")


def main():
    root = Path(__file__).resolve().parents[1]
    client = discover()
    status = client.request("/status")
    assert not status["busy"], "Return development client to title screen"
    try:
        build_opener(ProxyHandler({})).open(f"http://127.0.0.1:{client.port}/status")
        raise AssertionError("Unauthenticated request accepted")
    except HTTPError as error:
        assert error.code == 401
    source = root / ".debug-smoke" / "demo.py"
    source.parent.mkdir(exist_ok=True)
    shutil.copy2(root / "examples" / "debug_demo.py", source)
    initial = source.read_text(encoding="utf-8")
    session = Session(client, source, json.loads(CONFIG.read_text(encoding="utf-8")))
    try:
        session.prepare(status["username"])
        session.start()
        wait(lambda: session.ready.is_set() and session.active.is_set(), "Paper ready")
        client.request("/connect", session.payload("connecting"))
        session.connected = True
        wait(lambda: client.request("/status")["in_world"], "automatic world connection", 30)
        try:
            client.request("/connect", session.payload("connecting"))
            raise AssertionError("Busy client was switched")
        except DebugError as error:
            assert "title screen" in str(error)
        for command in ("pynner give debug_blade", "pynner spawn debug_target", "debugerror"):
            client.request("/command", {"command": command})
        wait(lambda: "Intentional error" in session.error, "Python handler error", 15)
        client.request("/session", session.payload("running"))
        assert "Intentional error" in client.request("/status")["session"]["error"]
        client.request("/inspect", {})
        time.sleep(1)
        assert client.request("/status")["in_world"], "Debug screen render crashed client"
        source.write_text(initial + '\nprint("PYNNER_FABRIC_RELOADED")\n', encoding="utf-8")
        session.source.sync()
        wait(lambda: any("Activated Python generation 2" in line for line in session.lines), "hot reload", 20)
        source.write_text(initial + "\ninvalid @@@@@ syntax\n", encoding="utf-8")
        session.source.sync()
        wait(lambda: "SyntaxError" in session.error, "invalid candidate rejection", 20)
        session.command("pynner status")
        wait(lambda: "generation=2" in session.runtime, "old generation preserved", 10)
        source.write_text(initial, encoding="utf-8")
        session.source.sync()
        wait(lambda: any("Activated Python generation 4" in line for line in session.lines), "repair reload", 20)
        print(f"PYNNER_FABRIC_SMOKE_OK session={session.directory}")
    finally:
        session.close()
    assert not client.request("/status")["in_world"]
    assert session.process.poll() == 0, "Paper did not stop cleanly"
    print("PYNNER_FABRIC_SHUTDOWN_OK")


if __name__ == "__main__":
    main()
