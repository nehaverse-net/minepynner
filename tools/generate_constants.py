"""Generate IDE-completable enums from the exact Paper API used by Maven."""
import argparse
import re
import subprocess
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", type=Path, required=True)
    parser.add_argument("--javap", default="javap")
    args = parser.parse_args()
    classes = []
    for simple, qualified in [("Material", "org.bukkit.Material"), ("EntityType", "org.bukkit.entity.EntityType")]:
        output = subprocess.check_output([args.javap, "-classpath", str(args.api), qualified], text=True)
        names = re.findall(r"public static final " + re.escape(qualified) + r" ([A-Z0-9_]+);", output)
        if not names:
            raise RuntimeError(f"No constants found for {qualified}")
        classes.append("class " + simple + "(StrEnum):\n" + "".join(f'    {name} = "{name}"\n' for name in names))
        print(simple, len(names))
    path = Path(__file__).resolve().parents[1] / "pynner-sdk/src/pynner/constants.py"
    path.write_text('"""Generated from Paper 1.21.11; regenerate with tools/generate_constants.py."""\nfrom enum import StrEnum\n\n\n' + "\n\n".join(classes), encoding="utf-8")


if __name__ == "__main__":
    main()
