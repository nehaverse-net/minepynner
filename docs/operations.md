# 反映と運用

[目次](index.md) | 関連：[困ったとき](troubleshooting.md)

## ファイルを整理する

```text
plugins/Pynner/
  config.yml
  messages.yml
  scripts/
    weapons/
    mobs/
    events/
    plugins/
  logs/
  runtime/
```

`scripts/`以下の`.py`を再帰的に読み込みます。
上の四つのサブフォルダーは整理用で、ほかの名前のフォルダーも使えます。
`runtime/`はPython環境を置くための場所として使えますが、venvを自動作成するわけではありません。

ファイル名が`_`で始まる`.py`と、`.`で始まるファイルやフォルダーは、自動で実行する対象から除きます。
`__pycache__`も読み込み対象ではありません。
`__init__.py`にライフサイクル処理を書いても、自動のエントリーポイントとしては実行しません。
`scripts/`外を指すエントリースクリプトのsymlinkは拒否します。

## 補助関数を別ファイルに置く

例えば`events/_texts.py`へ定数を置きます。

```python
WELCOME = "ようこそ！"
```

同じフォルダーの`events/welcome.py`では、相対importを使います。

```python
from pynner import PlayerJoinEvent, event
from ._texts import WELCOME


@event(PlayerJoinEvent)
def welcome(e: PlayerJoinEvent) -> None:
    e.player.send_message(WELCOME)
```

`_texts.py`は補助モジュールとしてimportされます。
デコレーターで登録するエントリーファイルを、ほかのエントリーファイルから別名でimportすると重複登録の原因になります。
共有コードは補助ファイルへ分けてください。
ファイル名とフォルダー名は、`welcome.py`などPythonのモジュール名として使える名前にすると整理しやすくなります。

## 手動で反映する

保存後に`/pynner reload`を実行します。
reloadは全スクリプトを別のPythonプロセスへ読み込み直します。
変更した一つの関数だけを入れ替える方式ではありません。

1. 新しいPythonプロセスで各ファイルを読み込みます。
2. `on_load`を呼び、武器、Mob、コマンドなどの登録を検証します。
3. 検証に成功したら定義を切り替えます。
4. 新しいRuntimeを有効にし、旧Runtimeを終了します。

構文エラー、`on_load`の例外、不正なMaterialなどで検証に失敗すると、動いている旧世代を維持します。
新しい世代番号が付いても、失敗した候補は有効な世代にはなりません。
有効化後の`on_enable`の例外や、スクリプトによる外部ファイル変更まで元に戻す保証はありません。

reloadすると、Pythonの変数、クラスのインスタンス、タスクの待ち時間を再作成します。
ゲーム内の既存アイテムやMobの保存データは、Pythonのメモリとは別です。
保存値が必要な場合はEntityのPDCや、用途に合うファイル保存などを使います。

## 保存したら自動反映する

`plugins/Pynner/config.yml`の該当値を変更し、Paperを再起動します。

```yaml
runtime:
  auto-reload: true
```

約750msの待ち時間を挟み、`.py`の内容変更、追加、削除を検出してreloadします。
補助ファイルも変更検出の対象です。
失敗したときはログを確認し、修正して保存すると次の候補を読み込みます。

この版は`.py`を監視します。
外部のJSONや画像、pipパッケージを変えただけでは自動反映を保証しません。
必要なら手動reloadやサーバー再起動を行ってください。
既定のauto-reloadは`false`です。

## 起動時と終了時の関数

各エントリーファイルに次の名前の関数を定義できます。

```python
from pynner import server


def on_load() -> None:
    print("設定を読み込みました。")


async def on_enable() -> None:
    await server.broadcast("このスクリプトが有効になりました。")


def on_disable() -> None:
    print("このスクリプトを終了します。")
```

| 関数 | 時点 | 用途 |
|---|---|---|
| `on_load()` | 候補として読み込んだ後、登録の切替前 | 設定の読み取り、準備、検証 |
| `on_enable()` | Runtimeが有効になった後 | Minecraftへの初期操作 |
| `on_disable()` | reloadによる旧世代終了、通常のサーバー停止 | ローカル資源の解放 |

これらはクラス内のメソッドではなく、ファイルのトップレベルの関数です。
通常の`def`と`async def`の両方を使えます。
トップレベルや`on_load`ではMinecraftへのSDK操作を呼び出せません。
`on_disable`は片付け用とし、旧世代のMinecraft操作が成功することを前提にしないでください。
強制終了やクラッシュ時には`on_disable`を呼べないことがあります。
通常の終了でも、片付けを無制限に待たず、旧Runtimeの終了待ちは最大約2秒です。

## 設定ファイルの全項目

| キー | 既定値 | 意味 |
|---|---|---|
| `python.executable` | `python` | wheelを入れたPython実行ファイル。絶対パスを指定すると環境違いを避けられる |
| `runtime.auto-reload` | `false` | `.py`の変更監視 |
| `runtime.startup-timeout-seconds` | `30` | 起動候補の応答を待つ時間の上限 |
| `runtime.heartbeat-timeout-seconds` | `15` | 有効Runtimeの生存通知が途絶えてから障害とする時間 |
| `runtime.restart-limit` | `3` | 自動再起動の最大試行回数。管理者のreloadでカウントをリセット |
| `runtime.restart-delay-seconds` | `5` | 障害検出後の再起動待ち時間 |
| `queues.operations` | `4096` | Java側の作業キューの件数上限。Runtimeからの操作や管理用作業を入れる |
| `queues.events` | `4096` | Java側の通常通知と低優先度通知、それぞれの件数上限 |
| `queues.operations-per-tick` | `200` | 1 tickで取り出す作業の最大件数 |
| `queues.operation-budget-ms` | `2.0` | 1 tickのキュー取り出しに使う時間予算（ms） |
| `queues.event-batch-size` | `128` | 1バッチへまとめる通知数。実装の範囲は1～256 |
| `debug.parent-pid` | `0`（無効） | Debugランチャー専用。指定した親プロセスが終了すると試験サーバーを停止。本番では設定しない |

時間予算は、すべてのMinecraft操作の実行時間が2ms以内に収まる保証ではありません。
後でSchedulerが実行する作業や、一件の長い処理は別に考慮する必要があります。
ほかにもバイト上限やPython側の固定上限があり、件数設定を増やすだけで欠落を防げるわけではありません。

設定は正の数を使い、自動再起動を止める場合の`restart-limit`は0にできます。
変更後はPaperを再起動してください。
`/pynner reload`はPythonの反映であり、config.ymlを読み直すコマンドではありません。

`messages.yml`で現在使用する変更項目は、reload要求受付の`reload`です。
同梱されている`prefix`と`not-ready`は、この版では表示の全箇所へ適用していません。
すべてのエラーメッセージをこのファイルで翻訳できるわけではありません。

## 状態を確認する

`/pynner status`を実行すると、次の値が出ます。

| 項目 | 意味 |
|---|---|
| `active` | 有効なPython Runtimeがあるか |
| `generation` | 現在のRuntime世代。プロセスを読み込み直すごとの識別番号 |
| `weapons` | 登録した武器定義の数。ゲーム内のアイテム個数ではない |
| `mobs` | 登録したMob定義の数。出現している個体数ではない |
| `operation_queue` | Java側で待っている作業数 |
| `event_queue` | Java側で送信を待っている通知数 |
| `dropped_events` | Java側で欠落または間引いた通知数の累計 |

`dropped_events`はPython側で破棄した数をすべて合算する値ではありません。
通常はキューが長時間増え続けていないことと、ログのエラーを確認します。
Runtimeが停止してもPaperは継続し、条件に従ってRuntimeを再起動します。
復旧まではPython関数による追加効果や返信を期待できません。

## ログと権限

| ファイル | 記録 |
|---|---|
| `logs/python-<世代>.log` | RuntimeのPython例外。スクリプト名、関数名、traceback |
| `logs/runtime-<世代>.log` | プロセスのstdoutとstderr。`print`や起動失敗も含む |
| Paperのconsoleと`logs/latest.log` | 起動、世代切替、登録エラー、Pythonエラーの転送など |

ハンドラーが連続5回失敗すると、その世代では実行を止めます。
修正後のreloadで新しい登録を作ります。
Pythonはサーバーと同じOSユーザー権限で動くため、管理者が確認したスクリプトを配置します。

| 管理コマンド | 権限 | 実行元 |
|---|---|---|
| `/pynner reload` | `pynner.admin.reload` | Player、console |
| `/pynner status` | `pynner.admin.status` | Player、console |
| `/pynner give <weapon_id>` | `pynner.admin.give` | Playerのみ |
| `/pynner spawn <mob_id>` | `pynner.admin.spawn` | Playerのみ |

管理権限の親は`pynner.admin`、既定はOPです。
独自Pythonコマンドのpermissionとは別に扱います。
