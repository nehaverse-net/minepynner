# 操作API

[目次](index.md) | 関連：[要素の関係](concepts.md)、[イベント](events.md)

## どのオブジェクトを使うか

| 対象 | 手に入れる例 | 用途 |
|---|---|---|
| `Player` | `e.player`、`ctx.player`、コマンドのPlayer引数 | メッセージ、所持品、満腹度など |
| `Entity` | `e.entity`、`e.target`、`await spawn_mob(...)` | HP、位置、速度、状態効果など |
| `World` | `e.world`、`entity.world`、`World("world")` | ワールド内への告知、設置 |
| `server` | `from pynner import server` | 全体告知、オンラインPlayer一覧、状態 |

PlayerにはEntityの操作も使えます。
APIはRuntimeが有効になった後のハンドラーや`on_enable`から呼び出します。

## 完了を待つ

ほとんどの操作メソッドは、**OperationReceipt**というawait可能な受領結果を返します。
呼び出した時点で要求を送るので、awaitしなくても操作を依頼します。

```python
def notify(player):
    player.send_message("処理を受け付けました。")


async def recover(player):
    await player.refresh()
    await player.set_health(player.max_health)
    await player.send_message("全回復しました。")
```

順序や完了確認が必要なときは、各操作を順番にawaitします。
操作が失敗すると`BridgeError`になり、awaitしなかった失敗もRuntimeがログへ出します。
タイムアウト時には応答だけを失った場合もあり、同じ副作用を自動で再送しません。

## Entityの読み取り

| 属性 | 型 | 意味 |
|---|---|---|
| `uuid` | `str` | 個体を識別するID |
| `name` | `str` | 名前。代入も可能 |
| `health` | `float` | 現在HPのスナップショット。代入も可能 |
| `max_health` | `float` | 最大HPのスナップショット |
| `location` | `Location` | 位置のスナップショット |
| `velocity` | `Vector` | 速度のスナップショット |
| `world` | `World` | スナップショットの位置から得たWorld |
| `pdc` | `dict` | 追加データの写し。辞書を書き換えてもゲーム側へ反映しない |

HPを持たないEntityへのHP操作は失敗します。
この版のEntityには、公開プロパティとしての`type`や`fire_ticks`はありません。
必要な取得項目があるかは、この表で確認してください。

`await entity.refresh()`はスナップショットを更新し、同じEntity参照を返します。
`entity.name = "名前"`と`entity.health = 20`は変更の依頼だけを送ります。
代入直後の読み取り値は自動更新されません。

## Entityの操作一覧

以下はEntityまたはPlayerへ呼び出すメソッドです。
数値は有限の値を使います。

| メソッド | 引数と動作 | 完了結果 |
|---|---|---|
| `set_health(value)` | 0以上、最大HP以下に設定。0なら死亡 | 通常`True` |
| `damage(amount)` | 0以上のダメージを依頼。防具などの影響を受ける | 通常`True` |
| `heal(amount)` | 0以上回復し、最大HPを上限とする | 通常`True` |
| `teleport(location)` | `Location`へ非同期移動 | 成功のbool |
| `kill()` | 生物はHPを0にし、ほかのEntityは除去 | 通常`True` |
| `set_velocity(velocity)` | `Vector`で速度を設定 | 通常`True` |
| `set_fire(seconds)` | 0以上の秒数を20 ticks／秒に換算して発火設定 | 通常`True` |
| `add_effect(effect, seconds, amplifier=0)` | 状態効果。強度は0～255 | 通常`True` |
| `remove_effect(effect)` | 状態効果を解除 | 通常`True` |
| `lightning(damage=False)` | 既定は雷の演出。Trueならダメージのある雷 | 通常`True` |
| `spawn_particle(particle, count=10)` | 現在位置へparticle。countは0～1000 | 通常`True` |
| `play_sound(sound, volume=1, pitch=1)` | 現在位置でsound。音量とpitchは0以上 | 通常`True` |
| `set_pdc(key, value)` | 追加データを保存 | 通常`True` |

状態効果の例は`"minecraft:speed"`、particleの例は`"FLAME"`、soundの例は`"entity.player.levelup"`です。
追加データが必要なparticleなど、種類によってはこの引数だけでは使用できずエラーになります。
`amplifier=0`はレベルI、`amplifier=1`はレベルIIに対応します。
`True`という完了結果はAPI呼び出しの実行を示し、最終ダメージなどの別途算出結果ではありません。

## 座標と速度

```python
from pynner import Location, Vector


async def launch(player):
    await player.teleport(Location("world", 0, 80, 0, yaw=0, pitch=0))
    await player.set_velocity(Vector(x=0, y=0.5, z=0))
```

| 型 | 引数 |
|---|---|
| `Location` | `world`、`x`、`y`、`z`が必須。`yaw=0`、`pitch=0`は向き |
| `Vector` | `x=0`、`y=0`、`z=0` |

ワールド名は実際に読み込まれている名前を使います。
座標は小数でも指定できます。
LocationとVectorは変更不可のdataclassなので、別の値が必要なら新しいインスタンスを作ります。

## Player専用の操作

| API | 意味 |
|---|---|
| `open_gui(gui_id, title, items, size=None)` | 選択GUIを開き、view_idを返す |
| `update_gui(view_id, items, clear=False)` | GUIのアイテムを更新 |
| `switch_gui(view_id, gui_id, title, items, size=None)` | GUIの画面を切り替え |
| `close_inventory(view_id)` | 指定した画面を閉じる |
| `sort_inventory(view_id)` | 通常のチェストを整頓 |
| `send_message(message)` | チャットメッセージ |
| `send_actionbar(message)` | 画面下のactionbar |
| `send_title(title, subtitle="")` | タイトルと副題 |
| `give_item(item, amount=1)` | アイテム追加。amountは1～64 |
| `remove_item(item, amount=1)` | 指定したアイテムを所持品からamount個取り除く |
| `food` | 満腹度。読み取りと代入が可能。設定は0～20 |
| `level` | 経験値レベル。読み取りと代入が可能。設定は0以上の整数 |
| `inventory.contents` | 読み取り用の`ItemSnapshot`または`None`のtuple |
| `inventory.give(item, amount=1)` | `give_item`と同じ依頼 |
| `inventory.remove(item, amount=1)` | `remove_item`と同じ依頼 |

`food`と`level`の代入は、完了結果を受け取る個別メソッドを公開していません。
アイテム授受では、Material名、武器ID、`ItemSpec`のいずれかを渡せます。

`await give_item(...)`の戻り値は、所持品に入り切らなかったアイテムの辞書listです。
自動では地面へ落としません。
空listならすべて所持品へ入りました。
管理コマンド`/pynner give`は、入り切らない分を地面に落とすため、このSDK操作と動作が異なります。

`remove_item`は先に個数を確認し、不足していればエラーにして取り除きません。
戻り値は取り除いた個数ではありません。
登録済み武器IDの場合はそのIDで比較します。
Material文字列やItemSpecの場合は生成したアイテムとのItemMetaを含む類似性で比較するため、独自名などを持つ同種アイテムは一致しない場合があります。

## 所持品の読み取り

```python
async def show_inventory(player):
    await player.refresh()
    for item in player.inventory.contents:
        if item is not None:
            print(item.material, item.amount, item.weapon_id)
```

ItemSnapshotの属性は`material`、`amount`、`name`、`weapon_id`、`damage`、`pdc`です。
`damage`は受けた耐久消費量で、攻撃力ではありません。
所持品のslot番号の対応はPaperのinventory配列に従い、画面のraw slot番号とは同一ではありません。
読み取り値の変更で所持品を編集するAPIはありません。

## 追加データを保存する

```python
async def mark(entity):
    await entity.set_pdc("example:quest", "tutorial")
    await entity.set_pdc("example:score", 10)
    await entity.refresh()
    print(entity.pdc.get("example:quest"))
```

値は文字列、整数、小数を使います。
bool、list、辞書などの複合値を保存するAPIではありません。
キーは小文字の名前空間と名前を使い、`example:quest`のように指定します。
名前空間を省くと`pynner:`を付けます。

予約済みのキーは`pynner:weapon_id`、`pynner:mob_id`、`pynner:schema`、`pynner:uses`、`pynner:durability`です。
保存したPDCはゲーム側の個体データに含まれます。
Entityが消滅した後までそのデータを保管する、外部のデータベースの代わりにはなりません。
この版にはPDCキーの削除操作はありません。

## Worldとserver

```python
from pynner import Location, World, server


async def prepare():
    world = World("world")
    await world.set_block(Location("world", 0, 64, 0), "GOLD_BLOCK")
    await world.broadcast("金ブロックを設置しました。")
    players = await server.online_players()
    print(f"オンライン人数: {len(players)}")
```

| API | 用途 |
|---|---|
| `World(name)` | 名前でWorldの参照を作る。作成時には存在確認しない |
| `world.name` | ワールド名 |
| `world.broadcast(message)` | そのワールドのPlayerへ告知 |
| `world.set_block(location, material)` | 指定座標のブロックを変更 |
| `world.spawn_mob(id, location)` | 登録済みMobを生成。awaitするとEntity |
| `spawn_mob(id, location)` | 同じ生成操作のルート関数 |
| `server.broadcast(message)` | サーバー全体へ告知 |
| `await server.online_players()` | オンラインPlayerのlist |
| `await server.status()` | Pynnerの状態辞書 |
| `world.set_weather(weather, seconds=600)` | ワールドの晴れ・雨・雷を設定。詳細は[GUI・天候](inventory-gui.md) |

`set_block`と`spawn_mob`の対象ワールドは、渡したLocationで決まります。
Worldの名前と別ワールドのLocationを混ぜないようにしてください。
`set_block`はゲーム状態を直接変更し、プレイヤーが設置したときの`block_place`イベントを模倣する操作ではありません。

## エラーを処理する

```python
from pynner.errors import BridgeError


async def heal_if_present(entity):
    try:
        await entity.heal(5)
    except BridgeError as error:
        print(f"回復できませんでした: {error.code}")
```

よくあるコードは`ENTITY_GONE`、`INVALID_ARGUMENT`、`TIMEOUT`、`STALE_GENERATION`です。
詳細は[困ったとき](troubleshooting.md)にあります。
