from pynner import BlockBreakEvent, PlayerMoveEvent, event


@event("block_break", material="DIAMOND_BLOCK", cancel=True)
def protect_diamond(e: BlockBreakEvent) -> None:
    if e.player is not None:
        e.player.send_message("Diamond blocks are protected")


@event(PlayerMoveEvent, rate_limit=2, min_distance=0.1)
def movement(e: PlayerMoveEvent) -> None:
    # This is an observation, not a synchronous Bukkit event callback.
    pass
