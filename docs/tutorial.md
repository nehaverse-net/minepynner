# 最初の機能を順番に作る

参加時の挨拶と回復コマンドを、一つのファイルへ順に追加します。
先に[Fabricクイックスタート](debug-quickstart.md)または[Paperへの導入](getting-started.md)を完了してください。

## 1. 挨拶だけを書く

自分の作業フォルダーに`lesson.py`をUTF-8で保存します。
本番で直接試す場合は`plugins/Pynner/scripts/plugins/lesson.py`へ保存します。

```python
from pynner import PlayerJoinEvent, event


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("Pythonの機能が動いています！")
```

`@event`は、Minecraftの出来事とPythonの関数を結び付けます。
`PlayerJoinEvent`はプレイヤーの参加通知で、`e.player`に参加したPlayerが入ります。
`send_message`がJava側へメッセージの送信を要求します。

Fabricでは`python -m pynner_debug D:/my-project/lesson.py`で実行します。
この段階のコードを`python lesson.py`だけで実行すると、関数を登録して終了します。
直接実行で試験ワールドを起動したい場合は、次のブロックをファイルの末尾へ追加します。

```python
if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
```

ランチャーを起動したターミナルは、編集と動作確認の間も実行したままにします。
保存すると自動で反映され、Ctrl+Cで終了します。

Paperへ配置した場合は`/pynner reload`を行います。
有効化後に参加すると挨拶が出ます。
すでに参加している人へ、過去の参加通知が再送されることはありません。

## 2. コマンドを追加する

上のファイルのimportを置き換え、コマンドを追加します。
次がファイル全体です。

```python
from pynner import CommandContext, PlayerJoinEvent, command, event


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("Pythonの機能が動いています！ /lessonhealで回復できます。")


@command("lessonheal", player_only=True)
async def heal(ctx: CommandContext) -> None:
    if ctx.player is None:
        return
    await ctx.player.refresh()
    await ctx.player.set_health(ctx.player.max_health)
    await ctx.reply("体力を回復しました。")


if __name__ == "__main__":
    from pynner.debug import run

    run(__file__)
```

保存して反映後、`/lessonheal`を実行します。
Survivalで減った体力が回復し、メッセージが出れば成功です。
`player_only=True`なのでconsoleからは実行できません。

### なぜasyncとawaitを使うのか

PythonとMinecraftは別プロセスです。
`refresh()`は現在のPlayerの状態を取得し、`set_health()`はJava側へ操作を要求します。
`await`は完了を待つため、この例では「回復に成功してから返信する」順序になります。

挨拶のように結果を待たない操作は通常の`def`から要求できます。
操作の結果や順番が必要な関数は`async def`で書きます。
取得済みの`health`や`location`はスナップショットなので、読むたびにサーバーへ問い合わせる値ではありません。

## 3. 引数を受け取る

回復コマンドを次へ置き換えます。

```python
@command("lessonheal", player_only=True)
async def heal(ctx: CommandContext, amount: float = 4.0) -> None:
    if ctx.player is None:
        return
    if amount <= 0:
        await ctx.reply("回復量は正の数で指定してください。")
        return
    await ctx.player.refresh()
    value = min(ctx.player.max_health, ctx.player.health + amount)
    await ctx.player.set_health(value)
    await ctx.reply(f"体力を{value}にしました。")
```

`/lessonheal 6`なら6ポイント回復し、省略すると4ポイント回復します。
引数の型`float`により数値へ変換されます。
入力の大小など機能固有の条件は、自分の関数で確認します。

## 4. エラーを直して反映を確かめる

保存後に反映されなければ、ターミナルかPaperログのtracebackを確認します。
構文エラーや登録エラーの場合、旧世代が動き続けるため、コマンドが残っていても新コードの成功とは限りません。
`/pynner status`と有効化ログで世代を確認します。

構文エラーを修正して再度保存、または`/pynner reload`を実行してください。
`lessonheal`を別ファイルにも登録すると重複エラーになります。
このチュートリアルの置換コードを、同じ名前の二つの関数として残さないでください。

## 次に作る機能

| 次の目標 | ページ |
|---|---|
| 命中時に特殊効果のある剣 | [武器とアイテム](weapons.md) |
| 練習用Mobやボス | [Mob](mobs.md) |
| ブロック保護など即時判定 | [イベントと宣言ルール](events.md) |
| お知らせを定期送信 | [コマンドと定期実行](commands-and-tasks.md) |
| 複数のファイルへ整理 | [プロジェクト構成](project-layout.md) |
| 本番サーバーへ移す | [本番への移行](deployment.md) |
