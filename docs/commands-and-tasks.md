# コマンドと定期実行

[目次](index.md) | 関連：[操作API](api.md)、[反映と運用](operations.md)

## 自分を回復するコマンド

`scripts/plugins/restore.py`へ保存します。

```python
from pynner import CommandContext, command


@command("restore", player_only=True)
async def restore(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    await ctx.player.refresh()
    await ctx.player.set_health(ctx.player.max_health)
    await ctx.reply("全回復しました。")
```

反映後、ゲーム内で`/restore`を実行します。
`ctx`は誰が実行したか、どの引数を受け取ったかを持つ**CommandContext**です。
`player_only=True`により、consoleからの実行を拒否します。
この例は操作完了を待ってから返信するため、`async def`で書いています。

## コマンド登録の項目

| 引数 | 既定値 | 意味 |
|---|---|---|
| `name` | 必須 | `/`を付けないコマンド名 |
| `aliases` | `None` | 別名のlist。例：`["pyrestore"]` |
| `permission` | `None` | 実行に必要な権限。省略時はPynnerによる個別権限チェックなし |
| `player_only` | `False` | `True`ならPlayerだけが実行可能 |
| `completions` | `None` | 引数名からTab候補listへの辞書 |
| `choices` | `None` | 引数名から許可する値のリストへの辞書。Tab候補にも使用 |
| `ranges` | `None` | 数値引数名から`(最小値, 最大値)`への辞書 |
| `error_messages` | `None` | 入力エラーの文面。引数ごとにも設定可能 |

コマンド名とaliasは英小文字から始め、英小文字、数字、`_`、`-`を使います。
Pynner内の重複や既存コマンドとの衝突は登録時に拒否します。
例えば対象のPaper 1.21.11にはMinecraftのテスト用`/test`があり、`@command("test")`は衝突します。
`@command("pynner_test")`など別の名前を使ってください。
`permission`を指定した場合の付与は、サーバーの権限管理で行ってください。
独自の権限名をdecoratorに書くだけでは、その権限のOP既定値や付与先を定義しません。

## 引数を型で指定する

```python
from pynner import CommandContext, Player, command


@command("rewardcoin", aliases=["rcoin"], permission="example.reward")
async def reward_coin(ctx: CommandContext, target: Player, amount: int = 1) -> None:
    if not 1 <= amount <= 64:
        await ctx.reply("個数は1～64で指定してください。")
        return
    leftovers = await target.give_item("GOLD_INGOT", amount)
    if leftovers:
        await ctx.reply("所持品に入り切らないアイテムがありました。")
    else:
        await ctx.reply("配布しました。")
```

実行例は`/rewardcoin Steve 5`です。
`amount`を省略すると1になります。
関数の最初の引数はCommandContextで、二つ目からがユーザーの入力です。

| 型 | 入力例 | 変換 |
|---|---|---|
| `str` | `hello` | 文字列一個 |
| `int` | `5` | 整数 |
| `float` | `1.5` | 小数を含む数値 |
| `bool` | `true`、`false` | 真偽値 |
| `Player` | `Steve` | 現在オンラインのPlayer |

型注釈を省くと`str`として扱います。
Player名とboolには型からTab候補を出します。
現在は引用符を使った複数単語引数、可変長引数、任意の独自型には対応しません。
数値の範囲は`ranges`で指定できます。
所持品の空きやゲーム内の状態など、機能ごとの条件は関数内で確認します。

## Tab候補とconsoleからの実行

```python
from pynner import CommandContext, command


@command("greet", completions={"language": ["ja", "en"]})
def greet(ctx: CommandContext, language: str = "ja") -> None:
    message = "こんにちは" if language == "ja" else "Hello"
    ctx.reply(f"{ctx.sender_name}さん、{message}")
```

consoleでは`greet en`と実行できます。
`completions`は入力候補で、入力値をそのlistに限定する検証ではありません。

| ctxの属性 | 意味 |
|---|---|
| `sender` | 送信元の参照。consoleは`console`、PlayerはUUID |
| `sender_name` | 表示用の送信元名 |
| `player` | 実行したPlayer。consoleなら`None` |
| `args` | 元の入力引数のtuple |
| `reply(message)` | 元の送信元へ返答する。await可能 |

## 選択肢と数値の範囲を指定する

例えば天候と継続秒数を指定するコマンドでは、次のように入力を制限します。
登録した制限はJava側で判定し、条件に合わない場合はPythonの関数を呼び出しません。

```python
from pynner import CommandContext, command

@command(
    "pynner_weather", player_only=True, permission="pynner.admin",
    choices={"mode": ["clear", "rain", "thunder"]},
    ranges={"seconds": (1, 86400)},
    error_messages={
        "mode.missing": "使い方: /pynner_weather <clear|rain|thunder> [秒数]",
        "mode.choices": "天候は {choices} から選んでください。",
        "seconds.invalid": "秒数は整数で入力してください。",
        "seconds.range": "秒数は {min}〜{max} にしてください。",
    },
)
async def weather(ctx: CommandContext, mode: str, seconds: int = 600) -> None:
    if ctx.player is not None:
        await ctx.player.world.set_weather(mode, seconds)
        await ctx.reply("天候を変更しました。")
```

`/pynner_weather rain 120`で雨を120秒に設定します。
`/pynner_weather snow`は選択肢のエラー、`/pynner_weather rain 0`は範囲のエラーになります。
`mode`を入力するときのTab候補にも`choices`が使われます。
同じ引数の`completions`も指定した場合、Tab表示は`completions`を優先し、値の制限は`choices`のままです。

`choices`の各値は引数の型と揃えます。
例えば`amount: int`なら`choices={"amount": [1, 2, 3]}`で、文字列の`"1"`は指定できません。
文字列は大文字と小文字を区別します。
Player型への`choices`指定はできません。
`ranges`はintまたはfloatに使い、両端を含みます。
片側だけ制限する場合は`(1, None)`のように指定します。
指定した引数名が存在しない場合、空の選択肢、大小が逆の範囲、制限外の既定値は登録時に拒否します。

## エラー文を設定する

`error_messages`にはエラーの種類をキーにした辞書を渡します。
`"range"`は全引数共通、`"seconds.range"`はseconds引数専用です。
専用の文面があれば共通の文面より優先します。
指定がないエラーは既定の文面で表示します。

| キー | 発生する場合 |
|---|---|
| `missing` | 必須引数がない |
| `invalid` | 型へ変換できない、指定Playerがオンラインでない |
| `choices` | 選択肢にない値 |
| `range` | 数値が範囲外 |
| `too_many` | 引数が多すぎる |
| `player_only` | Player専用コマンドをconsoleから実行 |
| `permission` | 実行権限がない |
| `unavailable` | Pythonを利用できない、通知キューが満杯 |

文面には`{command}`、`{argument}`、`{value}`、`{choices}`、`{min}`、`{max}`、`{detail}`を使えます。
対応する値がない箇所は空文字になります。
Python関数の実行中に発生した例外を置き換える機能ではありません。
操作時の`BridgeError`は関数内の`try`と`except`で扱います。

## 決まった間隔で実行する

`scripts/plugins/announcements.py`へ保存します。

```python
from pynner import scheduler, server


@scheduler.every(seconds=60)
def announcement() -> None:
    server.broadcast("困ったときは管理者へ相談してください。")


@scheduler.delay(seconds=5)
def ready() -> None:
    server.broadcast("Pythonの機能が有効になりました。")
```

`every`は繰り返し、`delay`は一回だけの実行です。
どちらも正の`seconds`を指定し、引数なしの関数を登録します。
登録が有効になってから時間を数えるため、reloadすると待ち時間も数え直します。

この秒数は20 ticksで1秒とするゲーム時間です。
サーバーが遅れている場合、実際の時計では間隔が長くなることがあります。
前の通知が未処理のタスクは、繰り返し分を際限なく積み上げません。

## タスクを止める

登録した関数には、キャンセル用の`task`が付けられます。
同じファイルのコマンドなどから操作します。

```python
@command("stopannounce", permission="example.admin")
async def stop_announcement(ctx: CommandContext) -> None:
    await announcement.task.cancel()
    await ctx.reply("定期告知を停止しました。")
```

この断片を前の例と同じファイルへ追加し、`command`と`CommandContext`もimportします。
タスクの実行時だけでなく、有効なRuntimeのハンドラーからキャンセルできます。
reloadすると、新しい定義からタスクが再登録されます。

## 時間のかかる処理

Runtimeは一つずつハンドラーを処理します。
`async def`で書いても、別のイベントハンドラーを無制限に並列実行するわけではありません。
現在の通常のasyncハンドラーには5秒の実行上限があります。
長時間の`time.sleep`、同期HTTP通信、重い計算などは、ほかの通知処理も止める原因になります。
Pynnerの`delay`は登録時に使うもので、任意の時点から待つ一般的な`setTimeout` APIではありません。
