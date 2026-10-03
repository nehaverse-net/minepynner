"""Verify the local Paper shutdown guard when an IDE forcibly stops Python."""
import json
import subprocess
import sys
import time
from pathlib import Path

from fabric_debug_smoke import wait


def child(marker: Path):
    from pynner_debug.runner import CONFIG, Session

    session = Session(None, Path(__file__).resolve().parents[1] / "examples" / "debug_demo.py",
                      json.loads(CONFIG.read_text(encoding="utf-8")))
    session.prepare("PynnerGuard")
    session.start()
    wait(lambda: session.ready.is_set() and session.active.is_set(), "guard server ready")
    marker.write_text(json.dumps({"directory": str(session.directory), "pid": session.process.pid}))
    while True:
        time.sleep(1)


def main():
    root = Path(__file__).resolve().parents[1]
    marker = root / ".debug-smoke" / "parent.json"
    marker.parent.mkdir(exist_ok=True)
    marker.unlink(missing_ok=True)
    with (marker.parent / "guard-test.log").open("w", encoding="utf-8") as output:
        process = subprocess.Popen([sys.executable, __file__, str(marker)], stdout=output, stderr=subprocess.STDOUT)
        try:
            wait(marker.exists, "child ready", 120)
            details = json.loads(marker.read_text())
            process.kill()
            process.wait(timeout=5)
            log = Path(details["directory"]) / "logs" / "latest.log"
            wait(lambda: "Debug runner exited; stopping" in log.read_text(encoding="utf-8"), "parent guard", 15)
            wait(lambda: "All dimensions are saved" in log.read_text(encoding="utf-8"), "world save", 15)
            print(f"PYNNER_PARENT_GUARD_OK {details['directory']}")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        child(Path(sys.argv[1]))
    else:
        main()
