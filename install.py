"""Install the three bundled wheels without publishing Pynner to PyPI."""

import argparse
import importlib.metadata
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
VERSION = "0.1.0"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check", action="store_true", help="Check requirements without installing"
    )
    args = parser.parse_args()
    if sys.version_info < (3, 11):
        raise SystemExit("Install Python 3.11 or later first.")
    wheels = [
        ROOT / "dist" / f"{name}-{VERSION}-py3-none-any.whl"
        for name in ("minepynner", "minepynner_runtime", "minepynner_debug")
    ]
    for wheel in wheels:
        if not wheel.is_file():
            raise SystemExit(f"Missing {wheel.name}. Extract the release ZIP completely first.")
    for legacy in ("pynner", "pynner-runtime", "pynner-debug"):
        try:
            importlib.metadata.distribution(legacy)
        except importlib.metadata.PackageNotFoundError:
            continue
        raise SystemExit(
            f"An older or unrelated package named {legacy} is installed. "
            "Use a separate Python environment or follow the migration guide before installing."
        )
    print(f"Python: {sys.executable}")
    if args.check:
        print("All bundled wheels found. No changes made.")
        return
    subprocess.run(
        [sys.executable, "-m", "pip", "install", "--force-reinstall", *map(str, wheels)], check=True
    )
    subprocess.run([sys.executable, "-m", "pip", "check"], check=True)
    print("Pynner installed. Next: https://nehaverse-net.github.io/minepynner/sharing.html")


if __name__ == "__main__":
    main()
