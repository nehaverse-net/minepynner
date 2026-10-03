"""Package the binaries and reproducible sources without local environments."""
from hashlib import sha256
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
EXCLUDED = {"target", "build", "__pycache__", "node_modules", ".pytest_cache", ".gradle", "run"}


def files(paths: list[str]):
    for relative in paths:
        path = ROOT / relative
        for candidate in sorted(path.rglob("*")) if path.is_dir() else [path]:
            parts = candidate.relative_to(ROOT).parts
            if candidate.is_file() and not candidate.name.endswith(".log") and not any(
                p in EXCLUDED or p.endswith(".egg-info") for p in parts
            ):
                yield candidate


def archive(name: str, paths: list[str]) -> None:
    with ZipFile(DIST / name, "w", ZIP_DEFLATED) as output:
        for path in files(paths):
            output.write(path, path.relative_to(ROOT).as_posix())


def main() -> None:
    DIST.mkdir(exist_ok=True)
    binaries = [
        "dist/pynner-paper-0.1.0.jar",
        "dist/pynner-0.1.0-py3-none-any.whl",
        "dist/pynner_runtime-0.1.0-py3-none-any.whl",
        "dist/pynner_debug-0.1.0-py3-none-any.whl",
        "dist/pynner-debug-fabric-0.1.0.jar",
    ]
    archive("pynner-0.1.0-release.zip", binaries + ["LICENSE", "README.md", "docs", "examples"])
    archive("pynner-0.1.0-docs.zip", ["LICENSE", "README.md", "docs", "examples"])
    archive("pynner-0.1.0-source.zip", [
        "pom.xml", "pyproject.toml", "mkdocs.yml", "requirements-docs.txt", ".gitignore", ".gitattributes", ".code-review-graphignore", "LICENSE", "README.md", "docs", "examples",
        "pynner-protocol", "pynner-paper", "pynner-sdk", "pynner-runtime", "tests", "tools",
        "pynner-debug", "pynner-fabric",
    ])
    names = binaries + [
        "dist/pynner-0.1.0-release.zip", "dist/pynner-0.1.0-source.zip",
        "dist/pynner-0.1.0-docs.zip",
    ]
    if (ROOT / "site" / "index.html").is_file():
        archive("pynner-0.1.0-docs-site.zip", ["LICENSE", "site"])
        names.append("dist/pynner-0.1.0-docs-site.zip")
    checksums = []
    for name in names:
        path = ROOT / name
        checksums.append(f"{sha256(path.read_bytes()).hexdigest()}  {path.name}\n")
        print(f"{path.name}: {path.stat().st_size:,} bytes")
    (DIST / "SHA256SUMS.txt").write_text("".join(checksums), encoding="utf-8")


if __name__ == "__main__":
    main()
