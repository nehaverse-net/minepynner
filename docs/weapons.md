# 武器とアイテム

[目次](index.md) | 関連：[イベント](events.md)、[操作API](api.md)

## 敵を燃やす剣を作る

`scripts/weapons/flame_blade.py`として保存します。

```python
from pynner import Event, Material, weapon


@weapon("flame_blade")
class FlameBlade:
    name = "§c炎の剣"
    material = Material.DIAMOND_SWORD
    damage = 12

    def on_hit(self, e: Event) -> None:
        if e.target is not None:
            e.target.set_fire(5)
```

反映して入手します。

```text
/pynner reload
/pynner give flame_blade
```

`@weapon`の引数が武器のIDで、`name`はゲーム内の表示名です。
`Material.DIAMOND_SWORD`は、見た目や基本の性質に使う既存のアイテム種類です。
`§c`は赤色の指定です。
独自の見た目を使う場合は、リソースパックも用意します。

`on_hit`は、有効な近接攻撃で最終ダメージが正になったときに呼ばれます。
`e.target`が攻撃された相手です。
この例は、通常の攻撃に加えて相手を5秒間燃やします。

## 定義できる全項目

項目はクラスの属性として書きます。
必要なものだけ指定できます。

| 項目 | 型と例 | 省略時 | 意味 |
|---|---|---|---|
| `name` | `str`：`"§c炎の剣"` | 元の表示名 | アイテムの表示名 |
| `material` | `Material`または`str` | `STICK` | 元にするアイテム種類 |
| `lore` | 文字列のtuple：`("説明", "二行目")` | 追加なし | 名前の下の説明文 |
| `custom_model_data` | `int`：`1001` | 指定なし | リソースパックで外見を選ぶ値 |
| `damage` | 0以上の数：`12` | 元の属性 | メインハンドの基礎攻撃属性 |
| `attack_speed` | 0以上の数：`1.6` | 元の属性 | メインハンドの攻撃速度属性 |
| `durability` | 正の`int`：`500` | 元の耐久値 | 最大耐久値。耐久を持つアイテムに限定 |
| `enchantments` | 辞書：`{"minecraft:unbreaking": 2}` | 追加なし | enchantの名前とレベル（1～255） |
| `attributes` | `AttributeModifier`のtuple | 追加なし | 攻撃以外を含む追加の属性補正 |
| `cooldown` | 0以上の秒数：`0.5` | `0` | 同じ使用者と武器IDの近接攻撃間隔 |
| `uses` | 正の`int`：`100` | 回数制限なし | 有効な近接命中の回数。尽きると一個消費 |
| `pdc` | 辞書：`{"example:element": "fire"}` | 追加なし | アイテムへ保存する追加データ |
| `recipe` | `Recipe(...)` | 登録なし | 形のあるクラフトレシピ |

`damage=12`は「どんな相手にも必ず12ダメージ」という意味ではありません。
防具、攻撃の充填、enchantなどが最終ダメージへ影響します。
`cooldown`は実時間で判定する追加の攻撃制限で、通常の攻撃速度属性とは別です。
弓やProjectileへ武器IDを引き継ぐ機能は、この版にはありません。

## レシピ、追加属性、追加データ

最初の例へ次の属性を加えられます。
追加した名前もimportします。

```python
from pynner import AttributeModifier, Event, Material, Recipe, weapon


@weapon("flame_blade")
class FlameBlade:
    name = "§c炎の剣"
    material = Material.DIAMOND_SWORD
    damage = 12
    attack_speed = 1.6
    durability = 500
    uses = 100
    cooldown = 0.5
    lore = ("敵を燃やす剣", "命中できる回数は100回")
    enchantments = {"minecraft:unbreaking": 2}
    attributes = (AttributeModifier("minecraft:luck", 1),)
    pdc = {"example:element": "fire"}
    recipe = Recipe(
        shape=(" D ", " D ", " S "),
        ingredients={"D": "DIAMOND", "S": "STICK"},
    )

    def on_hit(self, e: Event) -> None:
        if e.target is not None:
            e.target.set_fire(5)
```

このコードは前の例を置き換えて使います。
同じIDのクラスを二つ同時に置くと、重複登録になります。

`shape`の各文字列が作業台の一行です。
空白は空の枠、`D`と`S`は`ingredients`で指定した素材です。
形は1～3行、各行は同じ幅の1～3文字で指定します。
上の例では中央列にダイヤ二個、棒一本を置きます。

`AttributeModifier`の引数は次のとおりです。

| 引数 | 既定値 | 意味 |
|---|---|---|
| `attribute` | 必須 | 対象属性。例：`minecraft:luck` |
| `amount` | 必須 | 補正量 |
| `operation` | `ADD_NUMBER` | `ADD_NUMBER`は加算、`ADD_SCALAR`は基礎値に対する割合加算、`MULTIPLY_SCALAR_1`は`1 + amount`の乗算 |
| `slot` | `MAINHAND` | 有効な装備位置のグループ。例：`MAINHAND`、`OFFHAND`、`HEAD`、`CHEST`、`LEGS`、`FEET`、`HAND`、`ARMOR`、`ANY` |

PDCの値は文字列、整数、小数で指定します。
キーは`example:element`のような名前空間付きの名前にすると、他の用途と区別できます。
`pynner:weapon_id`などの内部キーは上書きできません。

## 全フックの引数

メソッドの形は`def on_hit(self, e: Event)`です。
フックの名前は固定ですが、引数の変数名`e`は変えられます。
完了待ちが必要なら`async def`にもできます。

| フック | 呼ばれる場面 | 受け取る主な値 |
|---|---|---|
| `on_left_click` | メインハンドの武器で左クリック操作 | `e.entity`、プレイヤーなら`e.player` |
| `on_right_click` | メインハンドの武器で右クリック操作 | `e.entity`、プレイヤーなら`e.player` |
| `on_hit` | 有効な近接攻撃が命中 | 使用者=`e.entity`、相手=`e.target`、最終ダメージ=`e.damage` |
| `on_kill` | 記録した武器攻撃の使用者が死亡原因に一致 | 使用者=`e.entity`、倒された相手=`e.target` |
| `on_damage` | 武器を持つ使用者がダメージを受ける | 使用者=`e.entity`と`e.target`、`e.damage` |
| `on_break` | 耐久による破損、または使用回数の消費 | 使用者=`e.entity`、プレイヤーなら`e.player` |
| `on_equip` | プレイヤーのメインハンドがこの武器IDに変わる | `e.player` |
| `on_unequip` | プレイヤーのメインハンドが別のIDなどに変わる | `e.player` |

左クリックのフックはPaperの操作イベントに対応し、戦闘の命中確認には`on_hit`を使います。
同じIDの剣から同じIDの剣へ持ち替えても、IDが変わらないのでequipの通知はありません。
撃破の関連付けには最後の有効な武器攻撃を10秒間記録し、死亡時の原因も照合します。
フックから受け取る値は読み取り用なので、元のダメージを`e.damage = ...`で変更できません。

`WeaponHitEvent`は型注釈用に同梱していますが、現在のRuntimeは武器フックを共通の`Event`としてデコードします。
実行時の判定に`isinstance(e, WeaponHitEvent)`を使わず、上の属性を参照してください。

## 普通のアイテムを配る

独自のフックが不要なら、武器クラスを作らず`ItemSpec`を使えます。
下の処理はプレイヤーを受け取るハンドラー内に書きます。

```python
from pynner import ItemSpec, Material


def reward(player):
    coin = ItemSpec(
        material=Material.GOLD_NUGGET,
        name="§e報酬コイン",
        lore=("クエストの報酬",),
        pdc={"example:kind": "reward"},
    )
    player.give_item(coin, amount=5)
```

`ItemSpec`で指定できる項目は`material`、`name`、`lore`、`custom_model_data`、`durability`、`enchantments`、`attributes`、`pdc`です。
武器専用の`damage`、`attack_speed`、`cooldown`、`uses`、`recipe`は含みません。
武器IDを指定する`player.give_item("flame_blade")`と、Materialを指定する`player.give_item("DIAMOND")`も使えます。
