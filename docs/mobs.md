# Mobを作る

[目次](index.md) | 関連：[武器](weapons.md)、[操作API](api.md)

## 報酬のあるゾンビを作る

`scripts/mobs/training_zombie.py`として保存します。

```python
from pynner import Entity, EntityType, Event, mob


@mob("training_zombie")
class TrainingZombie:
    base = EntityType.ZOMBIE
    name = "§a練習用ゾンビ"
    health = 40
    ai = False
    drops = [{"item": "DIAMOND", "amount": 1, "chance": 1.0}]
    experience = 10

    def on_spawn(self, entity: Entity) -> None:
        entity.spawn_particle("FLAME", count=10)

    def on_death(self, e: Event) -> None:
        if e.world is not None:
            e.world.broadcast("練習用ゾンビを倒しました。")
```

反映後、ゲーム内で実行します。

```text
/pynner reload
/pynner spawn training_zombie
```

自分の位置に、AIを停止したHP40のゾンビが出現します。
HPの単位はMinecraftの体力値で、ハート一個は2です。
この例のHP40はハート20個分です。
通常のゾンビなので、日光など元のEntityの性質も引き継ぎます。

## 定義できる全項目

| 項目 | 型と例 | 省略時 | 意味 |
|---|---|---|---|
| `base` | `EntityType`または`str`：`EntityType.ZOMBIE` | `ZOMBIE` | 元にする生物の種類 |
| `name` | `str`：`"§cボス"` | 元の状態 | 常時表示する名前 |
| `health` | 正の数：`500` | 元の最大HP | 最大HP。生成時のHPも最大にする |
| `attack_damage` | 0以上の数：`15` | 元の属性 | 攻撃属性 |
| `armor` | 0以上の数：`8` | 元の属性 | 防御属性 |
| `movement_speed` | 0以上の数：`0.25` | 元の属性 | 移動速度属性。ブロック毎秒の値ではない |
| `ai` | `bool`：`False` | 元の状態 | 自律行動の有効、無効 |
| `equipment` | 装備位置からアイテムへの辞書 | 元の装備 | 手持ちや防具 |
| `drops` | drop指定のlist | 通常のdrop | 指定すると通常dropを置き換える |
| `experience` | 0以上の整数：`50` | 通常の経験値 | 死亡時に落とす経験値 |
| `size` | 正の数：`1.2` | 元の属性 | `scale`属性。`1`が通常サイズ |
| `attributes` | 属性名から数値への辞書 | 追加変更なし | 属性の基礎値を設定する |
| `pdc` | 文字列、整数、小数の辞書 | 追加なし | 個体へ保存する追加データ |
| `tick_seconds` | 正の秒数：`1` | `1` | `on_tick`のゲーム時間での間隔 |

`base`には、生成可能なLivingEntityを指定します。
プレイヤーや矢などはMobのbaseにできません。
元の種類が持っていない属性を設定すると、登録時に拒否します。
例えば、すべての生物が近接攻撃の属性を持つわけではありません。

## 装備とdrop

クラスへ次のような属性を追加できます。
`ItemSpec`と`Material`もimportします。

```python
equipment = {
    "HAND": ItemSpec(Material.IRON_SWORD),
    "HEAD": ItemSpec(Material.IRON_HELMET),
}
drops = [
    {"item": "DIAMOND", "amount": 3, "chance": 1.0},
    {"item": "flame_blade", "amount": 1, "chance": 0.25},
]
attributes = {"minecraft:knockback_resistance": 0.5}
pdc = {"example:rank": "boss"}
```

`HAND`はメインハンドです。
装備位置には`HAND`、`OFF_HAND`、`HEAD`、`CHEST`、`LEGS`、`FEET`などの対象Entityが使える位置を指定します。
`BODY`と`SADDLE`は対応する種類向けです。
武器のAttributeModifierに使う`MAINHAND`とは名前が違います。

dropの`item`にはMaterial名、`ItemSpec`、登録済み武器IDを使えます。
`flame_blade`を使う場合は、その武器定義も同時に読み込まれている必要があります。
`amount`は1～64、`chance`は0～1で、各dropを独立に判定します。
`amount`の省略時は1、`chance`の省略時は1です。
`drops=[]`を指定すると、通常のdropもなくなります。

## 全フックの引数

| フック | 引数 | 呼ばれる場面と主な値 |
|---|---|---|
| `on_spawn` | `Entity` | Pynnerから新規生成した個体 |
| `on_tick` | `Entity` | `tick_seconds`間隔。読み込み中の有効な個体が対象 |
| `on_attack` | `Event` | Mobが攻撃。自身=`e.entity`、相手=`e.target`、`e.damage` |
| `on_damage` | `Event` | Mobが被弾。自身=`e.entity`と`e.target`、`e.damage` |
| `on_death` | `Event` | Mobが死亡。自身=`e.entity`、`e.world` |
| `on_target` | `Event` | 対象を変更。自身=`e.entity`、新対象=`e.target`（ない場合もある） |
| `on_move` | `Event` | Mobが移動。自身=`e.entity` |
| `on_interact` | `Event` | プレイヤーがMobを右クリック。自身=`e.entity`、操作した人=`e.player` |

`on_spawn`と`on_tick`の引数はEventではありません。
`entity.health`のように直接Entityを操作します。
ほかのフックでは`e.entity`を経由します。
`MobEvent`は型注釈用に同梱していますが、現在のRuntimeでは共通の`Event`としてデコードします。

読み込み直した既存Mobへ、`on_spawn`を再通知することはありません。
移動とtickの通知は過負荷時に間引かれるので、必ず毎回届くことを前提にしないでください。
死亡の通知後には個体が消えている場合があり、死亡した個体への変更操作は失敗し得ます。

## Pythonから生成する

コマンドやイベントの`async def`内では、生成した個体を受け取れます。

```python
from pynner import CommandContext, command, spawn_mob


@command("trainingmob", player_only=True)
async def training_mob(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    entity = await spawn_mob("training_zombie", ctx.player.location)
    await ctx.reply(f"生成しました: {entity.uuid}")
```

## reloadしたときの扱い

既存MobのIDはPDCに保存され、チャンク読み込みやサーバー再起動後も識別します。
reload後のフックは新しいPython定義へ切り替わります。
既存個体のHP属性や装備を、新しい定義で一括上書きする処理はありません。
属性の変更を確認したい場合は、新しい個体をspawnしてください。
