# チェストGUI・天候変更・整頓

`/cher`で天候選択GUIを開き、ボタンから晴れ・雨へ変更できます。
同じサンプルに、通常のチェストの空きマスを左クリックしたときの整頓も含めています。
コード全体は配布物の`examples/weather_chest.py`です。

## 起動して試す

更新したSDKとPaper JARが必要です。
Fabricでは更新したDebug wheelがPaper JARを同梱します。
すでに起動中の試験環境はCtrl+Cで停止し、Minecraftをタイトルへ戻してから実行します。

```powershell
python D:/pynner/examples/weather_chest.py
```

`/cher`を実行すると9マスのGUIが開きます。
左寄りのヒマワリは晴れ、右寄りの水入りバケツは雨です。
左クリックすると現在のワールドの天候を600秒に設定し、GUIを閉じます。
このコマンドの権限は`pynner.admin`で、既定はOPです。
雪になるか雨になるかは、天候とバイオームによるMinecraft側の動作です。

## GUIを開く

```python
from pynner import CommandContext, ItemSpec, command


@command("cher", player_only=True, permission="pynner.admin")
async def weather_menu(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    await ctx.player.open_gui(
        "weather_menu", "天候を選択", {
            2: ItemSpec(material="SUNFLOWER", name="§e晴れ"),
            6: ItemSpec(material="WATER_BUCKET", name="§b雨"),
        }, size=9,
    )
```

`gui_id`はPythonで画面を識別する名前です。
同じ表示タイトルの普通のチェストとは、`gui_id`で区別できます。
スロットは左上が0で、9マスなら0～8です。
sizeは9、18、27、36、45、54を指定できます。
itemsはスロット番号から`ItemSpec`またはMaterial名への辞書で、各ボタンは一個です。
空きマスは辞書へ書きません。

GUIは操作選択用で、クリック・Shiftクリック・数字キー・ドラッグによるアイテム移動をJava側でキャンセルします。
下段の所持品側の操作も、この画面を開いている間はキャンセルします。
通常のチェストと異なり、収納用のGUIではありません。
reloadとプラグイン終了では、PynnerのGUIを閉じます。

## 押したボタンを判定する

```python
from pynner import InventoryClickEvent, event


@event(InventoryClickEvent, include_cancelled=True)
async def choose_weather(e: InventoryClickEvent) -> None:
    if e.player is None or e.gui_id != "weather_menu":
        return
    if not e.in_top or e.click != "LEFT":
        return
    weather = {2: "clear", 6: "rain"}.get(e.slot)
    if weather is None:
        return
    await e.player.world.set_weather(weather, seconds=600)
    await e.player.close_inventory(e.view_id)
```

GUIのアイテム移動はキャンセル済みなので、通知に`include_cancelled=True`を指定します。
`in_top`は上段のチェスト部分のクリックかどうかです。
GUIの空きマスではweatherが見つからず、この例では何もしません。
`view_id`は開くたびに変わる識別子で、別画面や閉じた画面への操作を拒否するために使います。

## クリック情報

次の情報は`e.slot`などの専用プロパティで参照できます。
以前と同じ`e.data`のキーからも取得できます。
専用の`e.item`は`ItemSnapshot`、`e.data["item"]`は辞書です。

| キー | 意味 |
|---|---|
| `slot` | 画面全体のraw slot番号。画面外は負数の場合がある |
| `click` | `LEFT`、`RIGHT`、`SHIFT_LEFT`など |
| `view_id` | 現在の画面を指定する識別子 |
| `gui_id` | PynnerのGUI名。通常のインベントリは空文字 |
| `inventory_type` | 上段の種類。通常のチェストとGUIは`CHEST` |
| `top_size` | 上段のマス数 |
| `in_top` | 上段のマスならTrue |
| `empty` | クリック対象にアイテムがなければTrue |
| `cursor_empty` | カーソルに持っているアイテムがなければTrue |
| `item` | 対象アイテムのスナップショット辞書。空きマスならNone |

`item`は通知時点のMaterial、個数、表示名などの写しです。
辞書を書き換えてもインベントリへ反映しません。

## 二次元配列で画面と同じ並びにする

`items`には二次元配列を渡せます。
外側のリストが段、内側のリストが左から右の9マスです。
空きマスは`None`で表します。
段数から画面サイズを自動で決めるため、`size`を指定する必要はありません。

```python
from pynner import ItemSpec

D = ItemSpec("DIAMOND", name="真ん中！")
X = ItemSpec("BARRIER", name="閉じる")

await ctx.player.open_gui("my_menu", "メニュー", [
    [None, None, None, None, None, None, None, None, None],
    [None, None, None, None, D,    None, None, None, None],
    [None, None, None, None, None, None, None, None, X   ],
])
```

この例は3段27マスです。
二次元配列は1〜6段で、各段は必ず9マスに揃えます。
`size`も明示した場合は、段数×9と同じ値にします。
スロット番号をキーにした以前の辞書形式も使えます。
辞書形式で`size`を省略した場合は9マスです。

クリック位置も`e.row`と`e.column`で取得できます。
どちらも0から数えるため、中央のダイヤなら`e.row == 1 and e.column == 4`です。
下段のプレイヤー所持品や画面外のクリックでは、両方ともNoneになります。
以前の`e.slot`も使えます。

`switch_gui`と`update_gui`も同じ配列形式に対応します。
`update_gui`では指定した段の全マスを更新し、Noneのマスは空にします。
指定していない後ろの段は維持します（`clear=True`なら空になります）。
現在の画面より多い段数の更新はエラーです。
サイズ変更は`switch_gui`を使います。

## 開いた画面を更新する

同じ画面のアイテムを変更する場合、クリックで取得した`e.view_id`を指定します。
例えば「次へ」を押したとき、2番の表示を雨に変更できます。

```python
await e.player.update_gui(
    e.view_id,
    {2: ItemSpec("WATER_BUCKET", name="§b雨"), 4: None},
)
```

指定したスロットだけを変更します。
`None`でそのスロットを空にします。
`clear=True`なら先に全スロットを空にして、指定したアイテムを配置します。
`update_gui`では画面を閉じず、view_idも維持します。
タイトルやサイズを変える場合は次の`switch_gui`を使います。

## 確認画面へ切り替える

```python
new_view_id = await e.player.switch_gui(
    e.view_id, "weather_confirm", "雨にしますか？",
    {2: ItemSpec("LIME_WOOL", name="はい"),
     6: ItemSpec("RED_WOOL", name="キャンセル")},
)
```

切り替え後は新しいview_idが返ります。
次のクリックでは`e.gui_id == "weather_confirm"`と`e.slot`で決定かキャンセルかを判定します。
切り替え前のview_idによる更新や閉じる操作は拒否します。
更新と切り替えはPynnerが作ったGUIだけに使えます。
スロットやアイテムの指定が不正なら、更新途中の変更を残さずエラーにします。

ページ送りから確認までの実行例は`examples/weather_menu_pages.py`です。
このファイルを直接実行すると`/cherpages`と`/pynner_weather`を試せます。
選択状態はPlayerのUUIDごとに管理し、異なるプレイヤーの選択が混ざらないようにしています。

## 空きマスで通常のチェストを整頓する

```python
@event(InventoryClickEvent)
async def sort_chest(e: InventoryClickEvent) -> None:
    if e.player is None or e.cancelled or e.gui_id:
        return
    if e.inventory_type != "CHEST" or not e.in_top:
        return
    if not e.empty or not e.cursor_empty:
        return
    if e.click != "LEFT":
        return
    await e.player.sort_inventory(e.view_id)
```

整頓対象は現在開いている通常のチェストの上段です。
ダブルチェストも対象で、下段のプレイヤー所持品は変更しません。
Material名の順に並べ、メタデータが同じスタックだけを最大スタック数までまとめます。
名前・エンチャント・耐久値・PDCなどが異なるものはまとめません。
操作前に画面IDと空のカーソルを再確認し、閉じた画面の通知からの操作は拒否します。
Pynnerの選択GUI、かまど、ホッパー、エンダーチェストなどは整頓対象外です。
他プラグインによるキャンセル済みクリックは、このサンプルでは整頓しません。

## API一覧

| API | 戻り値と用途 |
|---|---|
| `await player.open_gui(gui_id, title, items, size=None)` | 新しい画面のview_id |
| `await player.update_gui(view_id, items, clear=False)` | 指定スロットを更新。Noneで削除 |
| `await player.switch_gui(view_id, gui_id, title, items, size=None)` | 現在のGUIを切り替え、新しいview_idを返す |
| `await player.close_inventory(view_id)` | 指定した画面が現在の画面なら閉じる |
| `await player.sort_inventory(view_id)` | 通常のチェストを整頓。成功時True |
| `await world.set_weather(weather, seconds=600)` | `clear`、`rain`、`thunder`。1～86400秒。成功時True |

天候は個人の見た目ではなく、指定ワールド全体へ適用します。
権限と操作できる人は、コマンドやイベントの登録側で決めます。
天候は他プラグインやゲームルールの影響も受けます。
