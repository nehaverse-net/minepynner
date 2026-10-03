# ファイルとプロジェクトの構成

スクリプトを保存する場所は、本番では`plugins/Pynner/scripts/`です。
Fabricデバッグでは自分の作業フォルダーを指定し、ランチャーが試験サーバーへコピーします。

## 一つのファイルから始める

```text
my-project/
  welcome.py
```

`welcome.py`へイベントやコマンドを書きます。
同じファイルに複数の機能を置けます。
ファイル名から武器やイベントを判定する仕組みではなく、デコレーターが登録内容を決めます。

ファイルを変更せずに試すには次を使います。

```powershell
python -m pynner_debug D:/my-project/welcome.py
```

直接`python welcome.py`で起動したい場合は、[直接実行するための末尾コード](fabric-debug.md#自分のファイルを直接実行できるようにする)を追加します。

## ファイルが増えたとき

```text
my-project/
  events/
    welcome.py
    _texts.py
  weapons/
    fire_sword.py
  mobs/
    training_zombie.py
  plugins/
    restore.py
```

フォルダー全体を試すには次を使います。

```powershell
python -m pynner_debug D:/my-project
```

本番では、この中身を`scripts/`へ置きます。
自分のエディター設定、venv、Git管理用ファイルはscriptsへ置く必要がありません。
Debugランチャーのコピー対象と除外対象の詳細は[Fabricガイド](fabric-debug.md)で確認できます。

## 補助モジュールを分ける

`events/_texts.py`:

```python
WELCOME = "ようこそ！"
```

`events/welcome.py`:

```python
from pynner import PlayerJoinEvent, event
from ._texts import WELCOME


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message(WELCOME)
```

`_`で始まるファイルは自動実行のエントリーポイントから外れますが、補助モジュールとしてimportできます。
`__init__.py`へ自動起動する機能を書くことは避け、通常のエントリーファイルへ書きます。
Pythonのモジュール名として使える英数字と`_`でファイル・フォルダー名を付けます。

デコレーター付きのエントリーファイルを、別名で重ねてimportしないでください。
同じ武器ID、Mob ID、コマンド名を二度登録するとreloadが失敗します。

## 保存先ごとの役割

| 場所 | 用途 | 変更の反映 |
|---|---|---|
| `scripts/**/*.py` | 実行するスクリプトと補助コード | `/pynner reload`またはauto-reload |
| `config.yml` | Python実行ファイル、監視、キュー設定 | Paper再起動 |
| `messages.yml` | 現在対応する表示メッセージ | Paper再起動 |
| `logs/` | Python関連のログ | 調査用 |
| `runtime/` | 必要ならサーバー用Python環境 | 自動作成されない |

通常のPython変数はreloadで作り直されます。
ワールドに保存するPDCと、Pythonプロセス内の変数の違いは[要素の関係](concepts.md)で説明しています。
