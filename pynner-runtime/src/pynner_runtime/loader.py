from __future__ import annotations

import inspect
import sys
import types
from pathlib import Path
from typing import Any

from pynner import _registry
from pynner._bridge import owner_context


class ScriptLoader:
    def __init__(self, root: Path):
        self.root = root.resolve()
        self.modules: list[tuple[str, types.ModuleType]] = []

    def load(self) -> dict[str, Any]:
        _registry.reset()
        for name in list(sys.modules):
            if name == "pynner_scripts" or name.startswith("pynner_scripts."):
                del sys.modules[name]
        sys.dont_write_bytecode = True
        sys.path.insert(0, str(self.root))
        package = types.ModuleType("pynner_scripts")
        package.__path__ = [str(self.root)]
        sys.modules[package.__name__] = package
        for path in sorted(self.root.rglob("*.py")):
            relative = path.relative_to(self.root)
            if any(
                part.startswith(".") or part == "__pycache__" for part in relative.parts
            ) or path.name.startswith("_"):
                continue
            if not path.resolve().is_relative_to(self.root):
                raise ValueError(f"Script symlink escapes scripts directory: {relative}")
            parts = list(relative.with_suffix("").parts)
            parent = "pynner_scripts"
            for index, part in enumerate(parts[:-1]):
                parent += "." + part
                if parent not in sys.modules:
                    namespace = types.ModuleType(parent)
                    namespace.__path__ = [str(self.root.joinpath(*parts[: index + 1]))]
                    sys.modules[parent] = namespace
            name = "pynner_scripts." + ".".join(parts)
            token = owner_context.set(relative.as_posix())
            try:
                # Compile source directly so rapid edits cannot reuse stale timestamp pyc.
                module = types.ModuleType(name)
                module.__file__ = str(path)
                module.__package__ = parent
                sys.modules[name] = module
                exec(compile(path.read_bytes(), str(path), "exec"), module.__dict__)
                self.modules.append((relative.as_posix(), module))
            finally:
                owner_context.reset(token)
        return _registry.registry.manifest()

    async def lifecycle(self, hook: str, report, *, fail_fast: bool = False) -> None:
        entries = reversed(self.modules) if hook == "on_disable" else self.modules
        for owner, module in entries:
            callback = getattr(module, hook, None)
            if callback is None:
                continue
            token = owner_context.set(owner)
            try:
                result = callback()
                if inspect.isawaitable(result):
                    await result
            except Exception:
                report(owner, hook)
                if fail_fast:
                    raise
            finally:
                owner_context.reset(token)
