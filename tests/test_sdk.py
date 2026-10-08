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


def test_chat_filter_manifest_copies_words():
    words = ["イキスギ", "お前やりませんねぇすぎぃ"]

    @event("async_chat", cancel=True, message_contains=words)
    def blocked(e):
        pass

    words.clear()
    handler = _registry.registry.manifest()["handlers"][0]
    assert handler["message_contains"] == ["イキスギ", "お前やりませんねぇすぎぃ"]
    assert handler["cancel"] is True
    event("async_chat", message_contains="word")(lambda e: None)
    assert _registry.registry.manifest()["handlers"][1]["message_contains"] == ["word"]


@pytest.mark.parametrize("words", [[], "", [""], [1], 123])
def test_empty_or_invalid_chat_filter_is_rejected(words):
    with pytest.raises(ValueError, match="message_contains"):
        event("async_chat", cancel=True, message_contains=words)


def test_chat_filter_is_rejected_on_other_events():
    with pytest.raises(ValueError, match="only supported"):
        event("player_join", message_contains="word")


def test_gui_and_weather_operations_and_validation():
    from pynner import ItemSpec, World

    async def run():
        calls = []

        class FakeBridge:
            def submit(self, operation, target, arguments, owner):
                calls.append((operation, arguments))
                future = asyncio.get_running_loop().create_future()
                future.set_result("view-token")
                return OperationReceipt(future)

        bind(FakeBridge())
        player = Player({"uuid": "p"})
        assert (
            await player.open_gui("weather", "Weather", {2: ItemSpec("SUNFLOWER")}) == "view-token"
        )
        await player.sort_inventory("view-token")
        await player.close_inventory("view-token")
        await World("world").set_weather("rain", 120)
        assert calls[0][1]["items"]["2"]["material"] == "SUNFLOWER"
        assert [call[0] for call in calls] == [
            "player.open_gui",
            "player.sort_inventory",
            "player.close_inventory",
            "world.set_weather",
        ]
        assert calls[3][1] == {"world": "world", "weather": "rain", "seconds": 120}
        with pytest.raises(ValueError):
            player.open_gui("weather", "Weather", {}, size=10)
        with pytest.raises(ValueError):
            player.open_gui("weather", "Weather", {9: "STONE"})
        with pytest.raises(ValueError):
            World("world").set_weather("snow")
        with pytest.raises(ValueError):
            World("world").set_weather("rain", 0)

    asyncio.run(run())


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


def test_event_properties_decode_optional_and_typed_values():
    from pynner import AsyncChatEvent, InventoryClickEvent, Location

    chat = decode_event({"event": "async_chat", "message": "こんにちは"})
    assert isinstance(chat, AsyncChatEvent) and chat.message == "こんにちは"
    click = decode_event(
        {
            "event": "inventory_click",
            "slot": 2,
            "click": "LEFT",
            "view_id": "v",
            "gui_id": "menu",
            "inventory_type": "CHEST",
            "top_size": 9,
            "in_top": True,
            "empty": False,
            "cursor_empty": True,
            "item": {"material": "STONE", "amount": 3, "name": "Named"},
        }
    )
    assert isinstance(click, InventoryClickEvent)
    assert (click.slot, click.view_id, click.gui_id, click.click) == (2, "v", "menu", "LEFT")
    assert click.in_top and not click.empty and click.cursor_empty and click.top_size == 9
    assert click.item.material == "STONE" and click.item.amount == 3
    assert InventoryClickEvent().item is None and InventoryClickEvent().slot == -1
    moved = decode_event(
        {"event": "player_move", "from": {"world": "world", "x": 1, "y": 2, "z": 3}}
    )
    assert moved.from_location == Location("world", 1, 2, 3) and moved.to_location is None
    death = decode_event(
        {"event": "entity_death", "entity": {"uuid": "p", "type": "PLAYER"}, "experience": 5}
    )
    assert death.entity_type == "PLAYER" and death.experience == 5
    with pytest.raises((FrozenInstanceError, AttributeError)):
        chat.message = "changed"
    assert chat.data["message"] == "こんにちは"


def test_command_constraints_manifest_and_copy():
    modes = ["clear", "rain"]
    messages = {"mode.choices": "Choose {choices}"}

    @command(
        "weather", choices={"mode": modes}, ranges={"seconds": (1, 60)}, error_messages=messages
    )
    def weather(ctx, mode: str, seconds: int = 30):
        pass

    modes.clear()
    messages.clear()
    handler = _registry.registry.manifest()["handlers"][0]
    assert handler["arguments"][0]["choices"] == ["clear", "rain"]
    assert handler["arguments"][1]["min"] == 1 and handler["arguments"][1]["max"] == 60
    assert handler["error_messages"]["mode.choices"] == "Choose {choices}"


@pytest.mark.parametrize(
    "options",
    [
        {"choices": {"missing": ["a"]}},
        {"choices": {"mode": []}},
        {"choices": {"mode": "rain"}},
        {"choices": {"mode": [1]}},
        {"ranges": {"mode": (1, 2)}},
        {"ranges": {"seconds": (2, 1)}},
        {"ranges": {"seconds": (float("nan"), 2)}},
        {"ranges": {"seconds": (True, 2)}},
        {"ranges": {"seconds": (0, 2)}},
        {"error_messages": {"other.choices": "error"}},
        {"error_messages": {"typo": "error"}},
    ],
)
def test_invalid_command_constraints_fail_registration(options):
    def callback(ctx, mode: str, seconds: int = 30):
        pass

    with pytest.raises(ValueError):
        command("weather", **options)(callback)


def test_gui_patch_and_switch_requests():
    async def run():
        calls = []

        class FakeBridge:
            def submit(self, operation, target, arguments, owner):
                calls.append((operation, arguments))
                future = asyncio.get_running_loop().create_future()
                future.set_result("next")
                return OperationReceipt(future)

        bind(FakeBridge())
        player = Player({"uuid": "p"})
        await player.update_gui("old", {2: None, 6: "STONE"}, clear=True)
        assert calls[0] == (
            "player.update_gui",
            {"view_id": "old", "items": {"2": None, "6": "STONE"}, "clear": True},
        )
        assert await player.switch_gui("old", "confirm", "Confirm", {2: "STONE"}) == "next"
        assert calls[1][1]["view_id"] == "old"
        with pytest.raises(ValueError):
            player.update_gui("old", {54: "STONE"})
        with pytest.raises(ValueError):
            player.switch_gui("old", "confirm", "Confirm", {9: "STONE"})

    asyncio.run(run())


def test_grid_gui_infers_size_and_coordinates():
    from pynner import InventoryClickEvent, ItemSpec

    async def run():
        calls = []

        class FakeBridge:
            def submit(self, operation, target, arguments, owner):
                calls.append((operation, arguments))
                future = asyncio.get_running_loop().create_future()
                future.set_result("view")
                return OperationReceipt(future)

        bind(FakeBridge())
        player = Player({"uuid": "p"})
        layout = [[None] * 9 for _ in range(3)]
        layout[1][4] = ItemSpec("DIAMOND", name="Center")
        layout[2][8] = "BARRIER"
        await player.open_gui("grid", "Grid", layout)
        assert calls[0][1]["size"] == 27
        assert set(calls[0][1]["items"]) == {"13", "26"}
        assert calls[0][1]["items"]["13"]["material"] == "DIAMOND"
        await player.switch_gui("view", "next", "Next", [[None] * 9] * 2)
        assert calls[1][1]["size"] == 18 and calls[1][1]["items"] == {}
        await player.update_gui("view", [[None, "STONE"] + [None] * 7])
        assert calls[2][1]["items"]["0"] is None
        assert calls[2][1]["items"]["1"] == "STONE"

    asyncio.run(run())
    clicked = InventoryClickEvent(data={"slot": 13, "in_top": True})
    assert (clicked.row, clicked.column) == (1, 4)
    outside = InventoryClickEvent(data={"slot": -999, "in_top": False})
    assert outside.row is None and outside.column is None
    bottom = InventoryClickEvent(data={"slot": 28, "in_top": False})
    assert bottom.row is None and bottom.column is None


@pytest.mark.parametrize(
    "layout,size",
    [
        ([], None),
        ([[None] * 8], None),
        ([[None] * 10], None),
        ([[None] * 9] * 7, None),
        ([[None] * 9] * 2, 9),
        (["123456789"], None),
        ([[1] + [None] * 8], None),
        ({True: "STONE"}, 9),
        ({9: "STONE"}, 9),
    ],
)
def test_invalid_gui_grid_fails_before_sending(layout, size):
    player = Player({"uuid": "p"})
    with pytest.raises(ValueError):
        player.open_gui("grid", "Grid", layout, size=size)
