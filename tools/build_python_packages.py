"""Build clean PyPI wheels and sdists without Windows editable-install file locks."""

import argparse
import shutil
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULES = ("pynner-sdk", "pynner-runtime", "pynner-debug")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--outdir", type=Path, default=ROOT / "dist" / "python")
    args = parser.parse_args()
    outdir = args.outdir.resolve()
    outdir.mkdir(parents=True, exist_ok=True)
    versions = {
        tomllib.loads((ROOT / module / "pyproject.toml").read_text(encoding="utf-8"))["project"][
            "version"
        ]
        for module in MODULES
    }
    if len(versions) != 1:
        raise SystemExit("SDK, Runtime and Debug versions must match")
    jar = ROOT / "pynner-debug/src/pynner_debug" / f"pynner-paper-{next(iter(versions))}.jar"
    if not jar.is_file():
        raise SystemExit(f"Build and copy the Paper plugin JAR first: {jar}")
    with tempfile.TemporaryDirectory(prefix="minepynner-build-") as temporary:
        for module in MODULES:
            source = ROOT / module
            stage = Path(temporary) / module
            stage.mkdir()
            for filename in ("pyproject.toml", "README.md", "LICENSE"):
                shutil.copy2(source / filename, stage / filename)
            shutil.copytree(
                source / "src",
                stage / "src",
                ignore=shutil.ignore_patterns("__pycache__", "*.egg-info", "*.pyc"),
            )
            # Default build creates an sdist, then builds the wheel from that sdist.
            subprocess.run(
                [sys.executable, "-m", "build", "--outdir", str(outdir), str(stage)], check=True
            )


if __name__ == "__main__":
    main()
