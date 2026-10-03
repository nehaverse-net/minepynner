import argparse
import asyncio
import os
from pathlib import Path

from .worker import Worker


def main() -> None:
    parser = argparse.ArgumentParser(description="Pynner CPython runtime")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--generation", type=int, required=True)
    parser.add_argument("--scripts", type=Path, required=True)
    parser.add_argument("--logs", type=Path, required=True)
    args = parser.parse_args()
    asyncio.run(
        Worker(
            "127.0.0.1",
            args.port,
            os.environ["PYNNER_TOKEN"],
            args.generation,
            args.scripts,
            args.logs,
        ).run()
    )


if __name__ == "__main__":
    main()
