from pynner import PlayerJoinEvent, event


@event("player_join")
def join(e: PlayerJoinEvent) -> None:
    e.player.send_message("Hello from Python!")
