"""Discover authenticated control endpoints published by running Fabric clients."""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

DEBUG_HOME = Path.home() / ".pynner" / "debug"


class DebugError(RuntimeError):
    pass


@dataclass(frozen=True)
class Client:
    descriptor: Path
    port: int
    token: str

    def request(self, path: str, payload: dict | None = None) -> dict:
        if not 1024 <= self.port <= 65535:
            raise DebugError("Invalid local control port")
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            f"http://127.0.0.1:{self.port}{path}",
            data=data,
            headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"},
        )
        try:
            # Loopback traffic must not travel through an OS-configured HTTP proxy.
            with build_opener(ProxyHandler({})).open(request, timeout=8) as response:
                return json.loads(response.read(65537))
        except HTTPError as error:
            detail = json.loads(error.read(65537)).get("error", str(error))
            raise DebugError(str(detail)) from None
        except (URLError, TimeoutError, OSError) as error:
            raise DebugError("Fabric debug client is unavailable") from error


def discover(client_id: str | None = None, directory: Path | None = None) -> Client:
    directory = directory or DEBUG_HOME / "clients"
    found = []
    for path in sorted(directory.glob("*.json")):
        if client_id and path.stem != client_id:
            continue
        try:
            config = json.loads(path.read_text(encoding="utf-8"))
            if config.get("minecraft") != "1.21.11":
                continue
            client = Client(path, int(config["port"]), config["token"])
            status = client.request("/status")
            if status.get("minecraft") == "1.21.11":
                found.append(client)
        except (DebugError, ValueError, KeyError, TypeError):
            continue
    if not found:
        raise DebugError("Fabric 1.21.11 with Pynner Debug must be running. Start it and return to the title screen.")
    if len(found) > 1:
        names = ", ".join(client.descriptor.stem for client in found)
        raise DebugError(f"Multiple clients found. Select --client ID: {names}")
    return found[0]
