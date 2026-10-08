# Pynner Python worker (`minepynner-runtime`)

The supervised CPython worker for the Pynner Minecraft Paper framework.
This distribution installs the `pynner_runtime` module and the `pynner-runtime`
command. It depends on the matching `minepynner` SDK and MessagePack.

After the first PyPI release:

```shell
python -m pip install "minepynner[server]"
```

Python 3.11 or later is required. A virtual environment is optional. Until the
first release, install the locally built SDK and worker wheels together.

The worker is launched and supervised by the matching Pynner Paper plugin JAR.
Configure `python.executable` in `plugins/Pynner/config.yml` to point at the Python
environment where these packages were installed. It uses authenticated loopback
IPC; it is not a replacement for the Paper server or a standalone Minecraft mod.

Scripts execute ordinary Python with the server account's permissions. Only load
scripts you trust. Runtime reload and failure handling do not sandbox Python.

[Japanese installation guide](https://github.com/nehaverse-net/minepynner/blob/main/docs/getting-started.md)
| [Repository](https://github.com/nehaverse-net/minepynner)

Targets Paper 1.21.11. Alpha software. Author: grampr. License: Apache-2.0.
