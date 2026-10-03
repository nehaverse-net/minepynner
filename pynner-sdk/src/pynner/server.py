from ._bridge import OperationReceipt, request


def broadcast(message: str) -> OperationReceipt:
    return request("server.broadcast", message=message)


async def online_players():
    from .player import Player

    return [Player(snapshot) for snapshot in await request("server.online_players")]


def status() -> OperationReceipt:
    return request("server.status")
