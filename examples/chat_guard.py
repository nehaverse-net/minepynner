"""Block listed chat substrings before delivery and privately warn the sender."""
from pynner import AsyncChatEvent, event


@event(
    AsyncChatEvent,
    message_contains=["イキスギ", "お前やりませんねぇすぎぃ"],
    cancel=True,
)
def check_chat(e: AsyncChatEvent) -> None:
    if e.player is not None:
        e.player.send_message("その言葉は使わないでください。")


if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
