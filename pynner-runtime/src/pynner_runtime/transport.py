import asyncio
import math
import struct
from typing import Any

import msgpack

MAX_FRAME = 1_048_576


def validate(value: Any, depth: int = 0) -> None:
    if depth > 32:
        raise ValueError("Excessive nesting")
    if value is None or isinstance(value, (bool, str, bytes)):
        return
    if isinstance(value, int):
        if not -(2**63) <= value < 2**63:
            raise ValueError("Integers must fit signed 64 bits")
        return
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("Non-finite number")
        return
    if isinstance(value, dict):
        if len(value) > 4096 or any(not isinstance(key, str) for key in value):
            raise ValueError("Invalid map")
        for item in value.values():
            validate(item, depth + 1)
        return
    if isinstance(value, list):
        if len(value) > 16384:
            raise ValueError("Array too large")
        for item in value:
            validate(item, depth + 1)
        return
    raise ValueError(f"Unsupported wire type: {type(value).__name__}")


def unique_map(pairs):
    result = {}
    for key, value in pairs:
        if not isinstance(key, str) or key in result:
            raise ValueError("Map keys must be unique strings")
        result[key] = value
    return result


def encode(message: dict[str, Any]) -> bytes:
    validate(message)
    payload = msgpack.packb(message, use_bin_type=True)
    if len(payload) > MAX_FRAME:
        raise ValueError("Frame exceeds 1 MiB")
    return struct.pack("!I", len(payload)) + payload


async def read(reader: asyncio.StreamReader) -> dict[str, Any]:
    length = struct.unpack("!I", await reader.readexactly(4))[0]
    if not 0 < length <= MAX_FRAME:
        raise ValueError("Invalid frame size")
    message = msgpack.unpackb(
        await reader.readexactly(length),
        raw=False,
        strict_map_key=True,
        max_str_len=MAX_FRAME,
        max_bin_len=MAX_FRAME,
        max_array_len=16384,
        max_map_len=4096,
        max_ext_len=0,
        object_pairs_hook=unique_map,
    )
    if not isinstance(message, dict):
        raise ValueError("Envelope must be a map")
    validate(message)
    return message


async def write(writer: asyncio.StreamWriter, message: dict[str, Any]) -> None:
    writer.write(encode(message))
    await writer.drain()
