"""Optional local debugging entrypoint; install minepynner-debug to use it."""
from pathlib import Path


def run(source: str | Path, **options):
    try:
        from pynner_debug import run as launch
    except ImportError as error:
        raise RuntimeError("Install the minepynner-debug wheel in this Python environment") from error
    return launch(source, **options)
