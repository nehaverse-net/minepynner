import asyncio

import pytest
from pynner import _registry
from pynner_runtime.loader import ScriptLoader


def test_nested_scripts_and_lifecycle(tmp_path):
    root = tmp_path / "scripts"
    (root / "events").mkdir(parents=True)
    (root / "events" / "hello.py").write_text(
        'from pynner import event\ncalls = []\n@event("player_join")\ndef join(e): pass\ndef on_load(): calls.append("load")\ndef on_enable(): calls.append("enable")\ndef on_disable(): calls.append("disable")\n',
        encoding="utf-8",
    )
    loader = ScriptLoader(root)
    manifest = loader.load()
    assert manifest["handlers"][0]["owner"] == "events/hello.py"

    async def run():
        for hook in ("on_load", "on_enable", "on_disable"):
            await loader.lifecycle(hook, lambda *a: pytest.fail(str(a)))

    asyncio.run(run())
    assert loader.modules[0][1].calls == ["load", "enable", "disable"]


def test_on_load_is_registration_only(tmp_path):
    (tmp_path / "bad.py").write_text(
        'from pynner import server\ndef on_load(): server.broadcast("bad")\n', encoding="utf-8"
    )
    loader = ScriptLoader(tmp_path)
    loader.load()
    with pytest.raises(RuntimeError):
        asyncio.run(loader.lifecycle("on_load", lambda *a: None, fail_fast=True))


def test_syntax_error_aborts_candidate(tmp_path):
    (tmp_path / "bad.py").write_text("def broken(:", encoding="utf-8")
    with pytest.raises(SyntaxError):
        ScriptLoader(tmp_path).load()


def test_rapid_same_size_edit_uses_source(tmp_path):
    script = tmp_path / "plugin.py"
    script.write_text(
        'from pynner import weapon\n@weapon("one")\nclass W: pass\n', encoding="utf-8"
    )
    assert "one" in ScriptLoader(tmp_path).load()["weapons"]
    script.write_text(
        'from pynner import weapon\n@weapon("two")\nclass W: pass\n', encoding="utf-8"
    )
    assert "two" in ScriptLoader(tmp_path).load()["weapons"]
    assert "one" not in _registry.registry.weapons
