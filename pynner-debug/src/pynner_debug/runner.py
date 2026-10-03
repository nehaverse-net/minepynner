from __future__ import annotations

import hashlib
import importlib.resources
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import uuid
from collections import deque
from pathlib import Path

from .client import DEBUG_HOME, Client, DebugError, discover

CONFIG = DEBUG_HOME / "config.json"
EXCLUDED = {"node_modules", "__pycache__", "venv", "target", "build"}


def accepted_eula(path: Path) -> bool:
    if not path.is_file():
        return False
    return any(line.strip().lower() == "eula=true" for line in path.read_text(encoding="utf-8").splitlines())


class ScriptSource:
    def __init__(self, source: Path, destination: Path):
        self.source = source.resolve()
        if not self.source.exists() or (self.source.is_file() and self.source.suffix != ".py"):
            raise DebugError("Specify a Python file or a scripts directory")
        self.single_file = self.source.is_file()
        self.root = self.source.parent if self.single_file else self.source
        self.destination = destination.resolve()
        self.hashes: dict[Path, str] = {}

    def paths(self):
        if self.single_file:
            candidates = [self.source, *self.root.glob("_*.py")]
        else:
            candidates = []
            for folder, directories, names in os.walk(self.root, followlinks=False):
                directories[:] = [name for name in directories if not name.startswith(".") and name not in EXCLUDED]
                candidates.extend(Path(folder) / name for name in names if name.endswith(".py"))
        for path in candidates:
            relative = path.relative_to(self.root)
            if any(part.startswith(".") or part in EXCLUDED for part in relative.parts):
                continue
            if not path.resolve().is_relative_to(self.root):
                raise DebugError(f"Script symlink escapes source directory: {relative}")
            yield path, relative

    def sync(self) -> bool:
        current = {}
        for path, relative in self.paths():
            try:
                content = path.read_bytes()
            except FileNotFoundError:
                continue  # An editor may replace a file while we scan it.
            current[relative] = hashlib.sha256(content).hexdigest()
            if self.hashes.get(relative) != current[relative]:
                target = (self.destination / relative).resolve()
                if not target.is_relative_to(self.destination):
                    raise DebugError("Destination path escapes session")
                target.parent.mkdir(parents=True, exist_ok=True)
                temporary = target.with_suffix(".pynner-tmp")
                temporary.write_bytes(content)
                temporary.replace(target)
        for relative in self.hashes.keys() - current.keys():
            target = (self.destination / relative).resolve()
            if not target.is_relative_to(self.destination):
                raise DebugError("Destination path escapes session")
            target.unlink(missing_ok=True)
        changed = current != self.hashes
        self.hashes = current
        return changed


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def link_or_copy(source, destination):
    try:
        os.link(source, destination)
        return destination
    except OSError:
        return shutil.copy2(source, destination)


class Session:
    def __init__(self, client: Client, source: Path, settings: dict):
        self.client, self.settings = client, settings
        self.id = uuid.uuid4().hex
        sessions = Path(settings.get("sessions", DEBUG_HOME / "sessions")).expanduser().resolve()
        self.directory = sessions / self.id
        self.directory.mkdir(parents=True, exist_ok=False)
        self.source = ScriptSource(source, self.directory / "plugins" / "Pynner" / "scripts")
        self.port = free_port()
        self.process: subprocess.Popen | None = None
        self.lock = threading.Lock()
        self.lines: deque[str] = deque(maxlen=200)
        self.error = ""
        self.runtime = ""
        self.ready = threading.Event()
        self.active = threading.Event()
        self.startup_failure = threading.Event()
        self.reader: threading.Thread | None = None
        self.connected = False

    def prepare(self, username: str):
        jar = Path(self.settings["paper_jar"]).expanduser().resolve()
        if not jar.is_file():
            raise DebugError(f"Paper JAR not found: {jar}")
        if not self.settings.get("accept_eula") and not accepted_eula(jar.parent / "eula.txt"):
            raise DebugError("Accept Minecraft EULA using configure --accept-eula, or supply an already accepted Paper directory.")
        shutil.copy2(jar, self.directory / "server.jar")
        for name in ("libraries", "cache", "versions"):
            folder = jar.parent / name
            if folder.is_dir():
                shutil.copytree(folder, self.directory / name, copy_function=link_or_copy)
        (self.directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        plugin = self.directory / "plugins"
        plugin.mkdir(exist_ok=True)
        explicit_plugin = self.settings.get("plugin_jar")
        if explicit_plugin:
            shutil.copy2(explicit_plugin, plugin / "pynner-paper-0.1.0.jar")
        else:
            resource = importlib.resources.files("pynner_debug") / "pynner-paper-0.1.0.jar"
            (plugin / "pynner-paper-0.1.0.jar").write_bytes(resource.read_bytes())
        self.source.sync()
        python_path = str(Path(sys.executable).resolve()).replace("\\", "/")
        (plugin / "Pynner" / "config.yml").write_text(
            f"python:\n  executable: {json.dumps(python_path)}\nruntime:\n  auto-reload: true\n"
            f"debug:\n  parent-pid: {os.getpid()}\n",
            encoding="utf-8",
        )
        properties = {
            "server-ip": "127.0.0.1", "server-port": self.port, "online-mode": "false",
            "enable-rcon": "false", "enable-query": "false", "level-name": "world",
            "level-type": "minecraft:flat", "generate-structures": "false", "gamemode": "creative",
            "generator-settings": json.dumps({"layers": [
                {"block": "minecraft:bedrock", "height": 1},
                {"block": "minecraft:dirt", "height": 2},
                {"block": "minecraft:grass_block", "height": 1},
            ], "biome": "minecraft:plains"}),
            "difficulty": "normal", "spawn-protection": 0, "view-distance": 4,
            "simulation-distance": 3, "max-players": 1, "pause-when-empty-seconds": -1,
            "motd": "Pynner Debug", "initial-enabled-packs": "vanilla",
        }
        (self.directory / "server.properties").write_text(
            "".join(f"{key}={value}\n" for key, value in properties.items()), encoding="utf-8",
        )
        if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", username):
            raise DebugError("Invalid Minecraft username")
        offline = bytearray(hashlib.md5(f"OfflinePlayer:{username}".encode()).digest())
        offline[6] = (offline[6] & 15) | 48
        offline[8] = (offline[8] & 63) | 128
        (self.directory / "ops.json").write_text(json.dumps([{
            "uuid": str(uuid.UUID(bytes=bytes(offline))), "name": username,
            "level": 4, "bypassesPlayerLimit": True,
        }]), encoding="utf-8")

    def start(self):
        java = self.settings.get("java", "java")
        memory = self.settings.get("memory", "2G")
        if not re.fullmatch(r"[1-9][0-9]*[MG]", memory):
            raise DebugError("Memory must be a JVM size such as 2G")
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        environment = dict(os.environ, PYTHONUTF8="1", PYTHONIOENCODING="utf-8")
        self.process = subprocess.Popen(
            [java, f"-Xmx{memory}", "-Dfile.encoding=UTF-8", "-jar", "server.jar", "nogui"],
            cwd=self.directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", env=environment, creationflags=flags,
        )
        self.reader = threading.Thread(target=self.read_log, daemon=True)
        self.reader.start()

    def read_log(self):
        assert self.process and self.process.stdout
        with (self.directory / "launcher.log").open("w", encoding="utf-8") as log:
            for line in self.process.stdout:
                log.write(line)
                log.flush()
                clean = line.rstrip()
                with self.lock:
                    self.lines.append(clean[:1000])
                    if "Done (" in clean:
                        self.ready.set()
                    if "Activated Python generation" in clean:
                        self.active.set()
                        self.error = ""
                    if not self.active.is_set() and (
                        "Registration failed:" in clean or "Python load failed" in clean
                    ):
                        self.startup_failure.set()
                    if "[Pynner] {" in clean:
                        self.runtime = clean.split("[Pynner] ", 1)[-1][:1024]
                    if any(marker in clean for marker in (
                        "Registration failed:", "Python Error", "SyntaxError:", "Error]:", "ERROR]:",
                    )) or re.search(r"\b\w+(Error|Exception):", clean):
                        self.error = clean[:512]
                if "[Pynner]" in clean or "PYNNER_" in clean:
                    print(clean, flush=True)

    def command(self, command: str):
        if self.process and self.process.poll() is None and self.process.stdin:
            try:
                self.process.stdin.write(command + "\n")
                self.process.stdin.flush()
            except (BrokenPipeError, OSError):
                return False
            return True
        return False

    def payload(self, state: str) -> dict:
        with self.lock:
            return {
                "id": self.id, "port": self.port, "state": state,
                "project": str(self.source.source), "directory": str(self.directory),
                "runtime": self.runtime, "error": self.error, "logs": list(self.lines)[-35:],
            }

    def close(self):
        if self.connected:
            try:
                self.client.request("/disconnect", {"id": self.id})
            except DebugError:
                pass
        if self.process and self.process.poll() is None:
            self.command("stop")
            try:
                self.process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                self.process.terminate()
                try:
                    self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait(timeout=5)
        if self.reader:
            self.reader.join(timeout=2)


def run(source: str | Path, *, client_id: str | None = None, settings: dict | None = None):
    """Start a dedicated Paper world, connect the running client, and watch source edits."""
    if settings is None:
        if not CONFIG.exists():
            raise DebugError("Run pynner-debug configure --paper-jar /path/to/Paper-1.21.11.jar first.")
        settings = json.loads(CONFIG.read_text(encoding="utf-8"))
    client = discover(client_id)
    status = client.request("/status")
    if status.get("busy"):
        raise DebugError("Return Minecraft to its title screen before debugging.")
    session = Session(client, Path(source), settings)
    print(f"Pynner debug world: {session.directory}", flush=True)
    try:
        session.prepare(status["username"])
        session.start()
        deadline = time.monotonic() + 120
        while not (session.ready.is_set() and session.active.is_set()):
            if session.startup_failure.is_set():
                raise DebugError(f"Python script failed to load. Inspect {session.directory / 'launcher.log'}")
            if session.process is None or session.process.poll() is not None:
                raise DebugError(f"Paper exited. Inspect {session.directory / 'launcher.log'}")
            if time.monotonic() > deadline:
                raise DebugError(f"Startup timed out. Inspect {session.directory / 'launcher.log'}")
            time.sleep(0.25)
        session.command("gamerule doDaylightCycle false")
        session.command("gamerule doWeatherCycle false")
        session.command("time set noon")
        client.request("/connect", session.payload("connecting"))
        session.connected = True
        print("Minecraft is connecting. Edit and save Python to reload. Ctrl+C stops this session.", flush=True)
        failures = 0
        next_status = 0.0
        while session.process and session.process.poll() is None:
            session.source.sync()
            try:
                client.request("/session", session.payload("running"))
                failures = 0
            except DebugError:
                failures += 1
                if failures >= 3:
                    raise DebugError("Minecraft client closed or became unavailable. Stopping local Paper.")
            if time.monotonic() >= next_status:
                session.command("pynner status")
                next_status = time.monotonic() + 5
            time.sleep(1)
        raise DebugError(f"Paper stopped. Inspect {session.directory / 'launcher.log'}")
    except KeyboardInterrupt:
        print("Stopping Pynner debug world...", flush=True)
    finally:
        session.close()
