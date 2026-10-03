import json
from pathlib import Path
from unittest.mock import Mock

import pytest
from pynner_debug.client import Client, DebugError, discover
from pynner_debug.runner import ScriptSource, Session, accepted_eula


def test_source_directory_tracks_changes_deletions_and_hidden_paths(tmp_path):
    source = tmp_path / "source"
    source.mkdir()
    (source / "main.py").write_text("x = 1")
    (source / "_helper.py").write_text("x = 2")
    (source / ".venv").mkdir()
    (source / ".venv" / "ignore.py").write_text("no")
    destination = tmp_path / "destination"
    scripts = ScriptSource(source, destination)
    assert scripts.sync()
    assert not scripts.sync()
    assert not (destination / ".venv").exists()
    (source / "main.py").write_text("x = 3")
    (source / "_helper.py").unlink()
    assert scripts.sync()
    assert (destination / "main.py").read_text() == "x = 3"
    assert not (destination / "_helper.py").exists()
    assert (source / "main.py").exists()


def test_single_file_does_not_load_other_plugins(tmp_path):
    source = tmp_path / "source"
    source.mkdir()
    for name in ("selected.py", "other.py", "_helper.py"):
        (source / name).write_text("pass")
    scripts = ScriptSource(source / "selected.py", tmp_path / "destination")
    scripts.sync()
    assert set(scripts.hashes) == {Path("selected.py"), Path("_helper.py")}
    (source / "selected.py").unlink()
    scripts.sync()
    assert set(scripts.hashes) == {Path("_helper.py")}
    assert not (scripts.destination / "other.py").exists()


@pytest.mark.parametrize("content,accepted", [("# eula=true", False), ("eula=false", False), ("eula=true\n", True)])
def test_eula_requires_real_acceptance(tmp_path, content, accepted):
    path = tmp_path / "eula.txt"
    path.write_text(content)
    assert accepted_eula(path) is accepted


def test_discovery_rejects_ambiguous_clients(tmp_path, monkeypatch):
    for index in (1, 2):
        (tmp_path / f"{index}.json").write_text(json.dumps({"minecraft": "1.21.11", "port": 4000 + index, "token": "test"}))
    monkeypatch.setattr(Client, "request", lambda self, path: {"minecraft": "1.21.11"})
    with pytest.raises(DebugError, match="Multiple clients"):
        discover(directory=tmp_path)
    assert discover("1", tmp_path).port == 4001


def test_discovery_ignores_dead_descriptors(tmp_path, monkeypatch):
    (tmp_path / "dead.json").write_text(json.dumps({"minecraft": "1.21.11", "port": 4001, "token": "test"}))
    monkeypatch.setattr(Client, "request", Mock(side_effect=DebugError("unavailable")))
    with pytest.raises(DebugError, match="must be running"):
        discover(directory=tmp_path)


def test_prepare_keeps_original_server_and_script_unchanged(tmp_path):
    server = tmp_path / "original"
    server.mkdir()
    (server / "server.jar").write_bytes(b"source")
    (server / "eula.txt").write_text("eula=true")
    (server / "world").mkdir()
    (server / "world" / "marker").write_text("existing world")
    plugin = tmp_path / "plugin.jar"
    plugin.write_bytes(b"plugin")
    source = tmp_path / "demo.py"
    source.write_text("pass")
    settings = {"paper_jar": str(server / "server.jar"), "plugin_jar": str(plugin), "sessions": str(tmp_path / "sessions")}
    session = Session(Mock(), source, settings)
    session.prepare("PynnerDev")
    properties = (session.directory / "server.properties").read_text()
    assert "server-ip=127.0.0.1" in properties
    assert "online-mode=false" in properties
    assert "gamemode=creative" in properties
    assert not (session.directory / "world").exists()
    assert (server / "world" / "marker").read_text() == "existing world"
    assert source.read_text() == "pass"
    operator = json.loads((session.directory / "ops.json").read_text())[0]
    assert operator["name"] == "PynnerDev" and operator["level"] == 4


def test_prepare_rejects_unaccepted_eula(tmp_path):
    jar = tmp_path / "server.jar"
    jar.write_bytes(b"jar")
    source = tmp_path / "demo.py"
    source.write_text("pass")
    session = Session(Mock(), source, {"paper_jar": str(jar), "sessions": str(tmp_path / "sessions")})
    with pytest.raises(DebugError, match="EULA"):
        session.prepare("PynnerDev")


def test_close_disconnects_only_owned_session_and_stops_process(tmp_path):
    source = tmp_path / "demo.py"
    source.write_text("pass")
    client = Mock()
    session = Session(client, source, {"sessions": str(tmp_path / "sessions")})
    session.connected = True
    process = Mock()
    process.poll.return_value = None
    session.process = process
    session.close()
    client.request.assert_called_once_with("/disconnect", {"id": session.id})
    process.stdin.write.assert_called_once_with("stop\n")
    process.wait.assert_called_once_with(timeout=30)


def test_client_rejects_invalid_control_port():
    with pytest.raises(DebugError, match="port"):
        Client(Path("unused"), 80, "test").request("/status")
