# Pynner local Minecraft debugger (`minepynner-debug`)

Run Pynner Python scripts in an isolated local Paper test world and connect an
already-running Minecraft client through the Pynner Fabric debug mod.
The distribution installs `pynner_debug` and the `pynner-debug` command.

After the first PyPI release:

```shell
python -m pip install "minepynner[debug]"
python -m pynner_debug --help
```

Until publication, install all three locally built Python wheels together.
Python 3.11 or later is required; a virtual environment is optional.

The package bundles the matching **Pynner plugin JAR**, not the Paper server JAR,
Minecraft client or Fabric mod. You also need Java 21 or later, a Paper 1.21.11
server JAR with its EULA accepted, and Minecraft Fabric 1.21.11 with the Pynner
debug mod installed. Configure the launcher as described in the guide.

Add the following to your script:

```python
if __name__ == "__main__":
    from pynner.debug import run
    run(__file__)
```

Return Minecraft to its title screen, then run `python main.py`. Save changes to
reload the script. Ctrl+C stops the debug session. This is a local test-server
launcher, not a Python breakpoint debugger.

[Japanese debug guide](https://github.com/nehaverse-net/minepynner/blob/main/docs/debug-quickstart.md)
| [Repository](https://github.com/nehaverse-net/minepynner)

Alpha software; Windows integration tested. Author: grampr. License: Apache-2.0.
