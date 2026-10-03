# イベントを使う

[目次](index.md) | 関連：[操作API](api.md)、[コマンド](commands-and-tasks.md)

## 通知する出来事を選ぶ

`@event`を付けた関数は、指定した出来事が起きたときに呼ばれます。
文字列の名前とイベントのクラスのどちらでも指定できます。

```python
from pynner import PlayerJoinEvent, event


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("参加ありがとうございます。")
```

`@event("player_join")`と同じ登録です。
関数は通常の`def`でも`async def`でも書けます。
Pythonへ通知するときには、Paper上の出来事の処理はすでに進んでいます。

## 共通の属性

| 属性 | 内容 |
|---|---|
| `e.player` | 通知の主体がプレイヤーの場合のPlayer。それ以外は通常`None` |
| `e.entity` | 通知の主体となるEntity |
| `e.target` | 攻撃やProjectileの相手など。なければ`None` |
| `e.attacker` | 攻撃者。提供されないイベントでは`None` |
| `e.world` | 通知が起きたWorld |
| `e.damage` | ダメージ通知の最終ダメージ。それ以外は通常`0` |
| `e.cancelled` | Java側で通知を作った時点のキャンセル状態 |
| `e.data` | 座標、クリック種類などの追加情報を含む元の辞書 |

参加イベントでは`e.player`が必ずあります。
すべてのイベントにプレイヤーが付いているわけではないので、共通処理では`None`を確認します。
`entity_damage_by_entity`の`e.entity`は被害者で、攻撃者には`e.attacker`を使います。

## 全13イベント

追加情報は`e.data`のキーで参照します。
例えばチャット本文は`e.data["message"]`です。

| 名前 | クラス | 主体と追加情報 | `cancel=True` |
|---|---|---|---|
| `player_join` | `PlayerJoinEvent` | 参加したプレイヤー | 不可 |
| `player_quit` | `PlayerQuitEvent` | 退出したプレイヤー | 不可 |
| `player_move` | `PlayerMoveEvent` | 移動した人。`from`、`to`の座標辞書 | 可 |
| `player_interact` | `PlayerInteractEvent` | 操作した人。`action`、`hand`、ブロック操作なら`material` | 可 |
| `entity_damage` | `EntityDamageEvent` | 被害者。`cause`と`e.damage` | 可 |
| `entity_damage_by_entity` | `EntityDamageByEntityEvent` | 被害者。`e.attacker`、`e.target`、`e.damage` | 可 |
| `entity_death` | `EntityDeathEvent` | 死亡した生物。`experience` | 不可 |
| `entity_spawn` | `EntitySpawnEvent` | 生成したEntity | 不可 |
| `block_break` | `BlockBreakEvent` | 壊した人。`material`、`block`の座標辞書 | 可 |
| `block_place` | `BlockPlaceEvent` | 置いた人。`material`、`block`の座標辞書 | 可 |
| `inventory_click` | `InventoryClickEvent` | 操作した人。`slot`はraw slot、`click`はクリック種類 | 可 |
| `projectile_hit` | `ProjectileHitEvent` | 命中したProjectile。命中先に応じ`e.target`または`block` | 可 |
| `async_chat` | `AsyncChatEvent` | 発言した人。`message`は装飾を除いた文字列 | 可 |

Entity同士の攻撃は、`entity_damage`と`entity_damage_by_entity`の両方へ通知される場合があります。
両方で同じ報酬処理を書いた場合に二重実行しないよう、用途に合う一方を選んでください。
spawn通知は元のPaperイベントの時点なので、Pynner固有の初期化済みMobを扱う場合は`on_spawn`を使います。

現在、`player_interact`はクリックしたアイテム自体やブロック座標を追加情報として提供しません。
`inventory_click`も、slotとclick以外のアイテム情報は専用には提供しません。
表にあるキーが取得できる範囲です。

## 条件に合う通知だけを受け取る

```python
from pynner import PlayerMoveEvent, event


@event(PlayerMoveEvent, world="world", min_distance=0.1, rate_limit=2)
def moved(e: PlayerMoveEvent) -> None:
    e.player.send_actionbar("移動を検知しました。")
```

| 引数 | 既定値 | 動作 |
|---|---|---|
| `world` | `None` | ワールド名が一致する通知だけを受け取る |
| `entity_type` | `None` | 主体Entityの種類が一致する通知だけを受け取る。例：`"ZOMBIE"` |
| `material` | `None` | 通知内の`material`が一致するものだけ。例：`"DIAMOND_BLOCK"` |
| `permission` | `None` | 主体がPlayerで、その権限を持つときだけ |
| `min_distance` | `0` | 移動通知の`from`と`to`の距離が指定値以上のものだけ |
| `rate_limit` | `0` | 正の値なら、ハンドラーと主体Entityごとの通知間隔を秒あたりの値で制限 |
| `include_cancelled` | `False` | ほかでキャンセル済みの通知も受け取る |
| `cancel` | `False` | 条件に一致する出来事をJava側で即時キャンセルする |

複数の条件はすべて満たす必要があります。
`material`はblock操作やブロックへのinteractなど、`material`を提供するイベントで使います。
Projectileのブロック命中では`block`はありますが`material`はないので、Material条件は一致しません。
`entity_type`が指すのは、攻撃者ではなく表の「主体」です。

`min_distance`は通知一回の移動量を判定し、前に通知した位置からの累積移動量ではありません。
`rate_limit=2`は同じ主体への通知をおおむね0.5秒以上空けます。
`0`は制限なしです。
過負荷時には移動通知がさらに間引かれます。

## ダイヤブロックを保護する

`scripts/events/protect.py`へ保存します。

```python
from pynner import BlockBreakEvent, event


@event("block_break", material="DIAMOND_BLOCK", cancel=True)
def protect(e: BlockBreakEvent) -> None:
    if e.player is not None:
        e.player.send_message("このブロックは保護されています。")
```

`cancel=True`とフィルターは、JavaがPaperイベント中に判定します。
Python関数は、判定後にメッセージなどの補足処理を行います。
Pythonが混雑して通知が届かなくても、登録済みのJavaルールは判定します。
ただし、後続の別プラグインがキャンセル結果を変更する場合があります。

`rate_limit`はPythonへの通知制限です。
上の保護に`rate_limit=1`を足しても、保護そのものを秒に一回へ制限するわけではありません。
自身が`cancel=True`で登録したルールの通知は、`include_cancelled=False`でも受け取ります。

Eventは読み取り用なので、関数内で`e.cancelled = True`を書いて元のイベントをキャンセルすることはできません。
「プレイヤーの独自データをPythonで確認して、その場の攻撃を即時キャンセルする」といった任意条件の同期判定は、この版のAPIにはありません。

## チャット内容をログへ出す

```python
from pynner import AsyncChatEvent, event


@event(AsyncChatEvent)
def chat(e: AsyncChatEvent) -> None:
    if e.player is not None and e.data is not None:
        print(f"{e.player.name}: {e.data['message']}")
```

`print`の出力はRuntimeのログに残ります。
AsyncChatのプレイヤー情報や権限には、メインスレッドで更新したキャッシュを使います。
状態に最大約10 ticksの遅れがある点を考慮してください。

## 追加座標をLocationへ変換する

```python
from pynner import BlockPlaceEvent, Location, event


@event(BlockPlaceEvent)
def placed(e: BlockPlaceEvent) -> None:
    if e.data is not None:
        location = Location(**e.data["block"])
        print(f"設置: {location.world} {location.x}, {location.y}, {location.z}")
```

`e.data["block"]`は辞書です。
`Location(**...)`で、そのキーをLocationの引数として渡しています。
