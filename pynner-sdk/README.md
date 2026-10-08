# Pynner Python SDK (`minepynner`)

Write ordinary Python scripts that add commands, events, weapons, mobs and chest
menus to a Minecraft Paper server. Minecraft operations run in the Java plugin;
your scripts run in a separate CPython process.

The distribution name is **minepynner**; the Python import name is **pynner**.
This project is unrelated to the existing `pynner` project on PyPI.

## Install

After the first PyPI release:

```shell
python -m pip install minepynner
python -m pip install "minepynner[server]"  # SDK + server worker
python -m pip install "minepynner[debug]"   # SDK + worker + local debug launcher
```

Python 3.11 or later is required. A virtual environment is optional.
Until publication, use the wheels from the repository's release build.

## Example

Save this as a script in `plugins/Pynner/scripts/`:

```python
from pynner import PlayerJoinEvent, event

@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("Hello from Python!")
```

Install the matching **Pynner Paper plugin JAR** on a Paper 1.21.11 server and
configure its Python executable. The SDK alone does not start a Minecraft server.
The developer-only Fabric mod supports Minecraft 1.21.11; ordinary players on
your production server do not need it.

Features include typed command arguments, choices and tab completion, read-only
event properties, GUI layouts using rows of nine cells, guarded menu updates,
weather changes, and chest sorting. Events are snapshots; immediate cancellation
uses Java-side declarative rules rather than assigning `e.cancelled` in Python.

[日本語ガイド / Japanese guide](https://github.com/nehaverse-net/minepynner/blob/main/docs/index.md)
| [Repository](https://github.com/nehaverse-net/minepynner)
| [Issues](https://github.com/nehaverse-net/minepynner/issues)

Alpha software. Windows/Paper 1.21.11 has integration coverage; Linux runtime
gameplay and Folia are not certified. Author: grampr. License: Apache-2.0.
