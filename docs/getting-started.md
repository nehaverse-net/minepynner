# 導入して挨拶を表示する

[目次](index.md) | 前：[要素の関係](concepts.md) | 次：[イベント](events.md)

## 配布物を確認する

配布ZIPを展開すると、`README.md`、`docs/`、`examples/`、`dist/`があります。
`dist/`のJARと二つのwheelを使用します。
以下は展開先が`D:/pynner`、Paperサーバーが`D:/minecraft-server`の場合の例です。
手元の保存先が異なる場合は、パスを置き換えてください。

Paper 1.21.11の起動環境とPython 3.11以上を先に用意します。
この版はクライアントにModを入れずに使えます。

## JARとPython環境を設定する

1. Paperを停止します。
2. JARを`D:/minecraft-server/plugins/`へコピーします。
3. Paperを一度起動し、`plugins/Pynner/`ができたら停止します。
4. 普段使うPythonへSDKとRuntimeをインストールします。

仮想環境は必須ではありません。
PowerShellで実行します。

```powershell
python --version
python -m pip install D:/pynner/dist/minepynner-0.1.0-py3-none-any.whl D:/pynner/dist/minepynner_runtime-0.1.0-py3-none-any.whl
python -c "import sys, pynner, pynner_runtime; print(sys.executable)"
```

最後のコマンドでPython実行ファイルの絶対パスを確認します。
例えば`C:/Python314/python.exe`なら、`plugins/Pynner/config.yml`の該当する値を次のようにします。
ファイル全体を置き換える必要はありません。

```yaml
python:
  executable: 'C:/Python314/python.exe'
runtime:
  auto-reload: false
```

`python.executable`には、wheelを入れたPythonを指定します。
実際の出力と異なるパスを例のまま使わないでください。
設定後、Paperを起動します。

初回起動ではPython設定前なので、Runtimeの起動エラーが出る場合があります。
二つのパッケージを導入し、設定後に起動し直します。
依存パッケージの`msgpack`はpipが取得するので、インストール時にネット接続が必要です。
このPynnerはPyPIへ未公開のため、`pip install minepynner`だけでは同梱版を導入できません。

`python`が見つからない場合の`py -3.12`の使い方、任意のvenv、Linuxでの設定は[Python環境](python-environment.md)にあります。

## 起動を確認する

Paperのconsoleで`pynner status`を実行します。
`active=true`とログの`Activated Python generation ...`が出ればRuntimeが有効です。
`active=false`のままなら、Pythonの実行パスとimportの確認から始めます。
詳しくは[困ったとき](troubleshooting.md)を参照してください。

## 最初のスクリプトを置く

`plugins/Pynner/scripts/events/welcome.py`を作り、UTF-8で保存します。

```python
from pynner import PlayerJoinEvent, event


@event("player_join")
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message("ようこそ！ Pythonからの挨拶です。")
```

| 行 | 意味 |
|---|---|
| `from pynner import ...` | SDKから使う要素を取り込む |
| `@event("player_join")` | プレイヤーの参加通知に、この関数を登録する |
| `def welcome(e: PlayerJoinEvent)` | 通知を`e`という変数で受け取る |
| `e.player` | 参加したプレイヤー |
| `.send_message(...)` | そのプレイヤーへチャットメッセージを送る |

ゲーム内でOP権限のあるプレイヤーから実行します。

```text
/pynner reload
/pynner status
```

consoleでは先頭の`/`を省きます。
reloadは非同期に進むため、要求受付の表示だけでは成功を判断できません。
ログの`Activated Python generation ...`と、statusの`active=true`を確認してください。
読み込みが済んだら、一度退出して参加し直すと挨拶が届きます。
すでに参加している人へ、参加イベントをさかのぼって通知することはありません。

## 変更を反映する

メッセージを書き換えて保存し、再び`/pynner reload`を実行します。
保存だけで反映したい場合は、[Hot Reload](operations.md#保存したら自動反映する)を設定します。

`.py`の保存先は`plugins/Pynner/scripts/`以下です。
`weapons/`、`mobs/`、`events/`、`plugins/`は整理用なので、フォルダー名で処理の種類を決めているわけではありません。
一つのファイルにイベント、コマンド、武器をまとめることもできます。

## エディターの補完を使う

エディターで使うPythonにもSDKを導入すると、`Player`などの補完と型情報を利用できます。
サーバーと同じPythonをエディターで選べば、同じSDKを使えます。
別の開発用Pythonを選ぶ場合は、その環境へSDKのwheelをインストールしてください。

ゲームへの操作はPaperから起動したRuntime内で実行します。
エディターの「Pythonファイルを実行」で`welcome.py`を起動する必要はありません。
