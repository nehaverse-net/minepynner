import asyncio
from dataclasses import FrozenInstanceError

import pytest
from pynner import (
    CommandContext,
    Entity,
    Player,
    PlayerJoinEvent,
    _registry,
    command,
    event,
    mob,
    scheduler,
    weapon,
)
from pynner._bridge import OperationReceipt, bind
from pynner.event import decode_event


@pytest.fixture(autouse=True)
def clean_registry():
    _registry.reset()
    bind(None)
    yield
    bind(None)


def test_decorators_emit_typed_manifest():
    @event(PlayerJoinEvent)
    def join(e):
        pass

    @command("gift", aliases=["present"])
    def gift(ctx: CommandContext, player: Player, amount: int):
        pass

    @scheduler.every(seconds=1)
    def tick():
        pass

    @weapon("sword")
    class Sword:
        material = "DIAMOND_SWORD"

        def on_hit(self, e):
            pass

    @mob("boss")
    class Boss:
        health = 40

    manifest = _registry.registry.manifest()
    assert manifest["handlers"][0]["event"] == "player_join"
    assert [a["type"] for a in manifest["handlers"][1]["arguments"]] == ["Player", "int"]
    assert manifest["weapons"]["sword"]["hooks"]["on_hit"]
    assert manifest["mobs"]["boss"]["health"] == 40
    assert tick.task.id


def test_invalid_registration_is_rejected():
    with pytest.raises(ValueError, match="Unknown event"):
        event("unknown")
    with pytest.raises(ValueError, match="not cancellable"):
        event("player_join", cancel=True)
    with pytest.raises(ValueError, match="positive"):
        scheduler.every(seconds=0)
    with pytest.raises(Exception, match="lowercase"):
        weapon("Invalid")(type("Sword", (), {}))


def test_snapshot_is_typed_and_immutable():
    decoded = decode_event(
        {
            "event": "player_join",
            "player": {"uuid": "p", "type": "PLAYER", "health": 20},
            "entity": {"uuid": "p", "type": "PLAYER"},
            "world": "world",
        }
    )
    assert isinstance(decoded, PlayerJoinEvent)
    assert isinstance(decoded.player, Player)
    assert decoded.player.health == 20
    with pytest.raises(FrozenInstanceError):
        decoded.cancelled = True


def test_operations_require_activation():
    with pytest.raises(RuntimeError, match="active"):
        Entity({"uuid": "e"}).set_fire(5)


def test_mutations_and_awaitable_receipts():
    async def run():
        requests = []

        class FakeBridge:
            def submit(self, operation, target, arguments, owner):
                requests.append((operation, target, arguments))
                future = asyncio.get_running_loop().create_future()
                future.set_result(True)
                return OperationReceipt(future)

        bind(FakeBridge())
        player = Player({"uuid": "e", "health": 10})
        player.health = 20
        assert player.health == 10  # Reads stay explicit snapshots.
        assert await player.set_fire(5) is True
        assert requests == [
            ("entity.set_health", {"uuid": "e"}, {"value": 20}),
            ("entity.set_fire", {"uuid": "e"}, {"seconds": 5}),
        ]

    asyncio.run(run())
