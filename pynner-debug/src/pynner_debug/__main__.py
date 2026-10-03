from __future__ import annotations

import argparse
import json
from pathlib import Path

from .client import DEBUG_HOME, DebugError, discover
from .runner import CONFIG, run


def main():
    parser = argparse.ArgumentParser(description="Pynner Python debugging with a running Fabric 1.21.11 client")
    parser.add_argument("source", nargs="?", help="Python file, scripts folder, configure, or clients")
    parser.add_argument("--client", help="Client descriptor ID if multiple Minecraft instances run")
    parser.add_argument("--command", help="Submit a command to the active local debug world")
    parser.add_argument("--paper-jar", type=Path)
    parser.add_argument("--plugin-jar", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--memory", default="2G")
    parser.add_argument("--accept-eula", action="store_true", help="Accept https://aka.ms/MinecraftEULA for local test worlds")
    arguments = parser.parse_args()
    try:
        if arguments.command:
            discover(arguments.client).request("/command", {"command": arguments.command})
            print("Command submitted to local debug world")
        elif arguments.source == "configure":
            if arguments.paper_jar is None or not arguments.paper_jar.is_file():
                parser.error("configure requires --paper-jar pointing to Paper 1.21.11")
            config = {
                "paper_jar": str(arguments.paper_jar.resolve()), "java": arguments.java,
                "memory": arguments.memory, "accept_eula": arguments.accept_eula,
            }
            if arguments.plugin_jar:
                config["plugin_jar"] = str(arguments.plugin_jar.resolve())
            DEBUG_HOME.mkdir(parents=True, exist_ok=True)
            CONFIG.write_text(json.dumps(config, indent=2, ensure_ascii=False), encoding="utf-8")
            print(f"Configuration saved: {CONFIG}")
        elif arguments.source == "clients":
            client = discover(arguments.client)
            print(client.descriptor.stem)
            print(json.dumps(client.request("/status"), ensure_ascii=False, indent=2))
        elif arguments.source:
            run(arguments.source, client_id=arguments.client)
        else:
            parser.print_help()
    except (DebugError, OSError, ValueError) as error:
        parser.exit(1, f"Pynner Debug: {error}\n")


if __name__ == "__main__":
    main()
