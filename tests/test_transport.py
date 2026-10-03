import asyncio
import struct

import pytest
from pynner_runtime.transport import MAX_FRAME, encode, read


def decode(frame):
    async def run():
        reader = asyncio.StreamReader()
        reader.feed_data(frame)
        reader.feed_eof()
        return await read(reader)

    return asyncio.run(run())


def test_unicode_and_primitive_round_trip():
    message = {
        "version": 1,
        "type": "event",
        "payload": {"name": "炎の剣", "hp": 12.5, "values": [True, None, -5]},
    }
    assert decode(encode(message)) == message


@pytest.mark.parametrize("size", [0, MAX_FRAME + 1])
def test_frame_limit(size):
    with pytest.raises(ValueError):
        decode(struct.pack("!I", size))


def test_truncated_frame():
    with pytest.raises(asyncio.IncompleteReadError):
        decode(struct.pack("!I", 10) + b"abc")


def test_oversized_outgoing_frame():
    with pytest.raises(ValueError):
        encode({"data": "x" * MAX_FRAME})


@pytest.mark.parametrize("value", [float("nan"), float("inf"), 2**63, -(2**63) - 1])
def test_nonportable_numbers_are_rejected(value):
    with pytest.raises(ValueError):
        encode({"value": value})


def test_duplicate_keys_are_rejected():
    payload = b"\x82\xa1a\x01\xa1a\x02"
    with pytest.raises(ValueError, match="unique"):
        decode(struct.pack("!I", len(payload)) + payload)


def test_excessive_nesting_is_rejected():
    value = "leaf"
    for _ in range(40):
        value = [value]
    with pytest.raises(ValueError, match="nesting"):
        encode({"value": value})
