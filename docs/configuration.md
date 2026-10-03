# 設定ファイル

設定ファイルは`plugins/Pynner/config.yml`です。
Python実行ファイル、変更監視、障害時の再起動、処理キューを設定します。
変更した場合はPaperを再起動します。

## 初めに変更する項目

`python.executable`を、SDKとRuntimeを入れたPythonの絶対パスへ変更します。
仮想環境か通常のPythonかは問いません。

```powershell
python -c "import sys; print(sys.executable)"
```

普段のPythonを使う例です。

```yaml
python:
  executable: 'C:/Python314/python.exe'
runtime:
  auto-reload: true
```

`auto-reload`は開発時に保存だけで反映したい場合に有効にします。
本番の変更を管理してから反映したい場合は、既定の`false`と手動reloadを使えます。

## 配布時のconfig.yml

```yaml
python:
  executable: python
runtime:
  auto-reload: false
  startup-timeout-seconds: 30
  heartbeat-timeout-seconds: 15
  restart-limit: 3
  restart-delay-seconds: 5
queues:
  operations: 4096
  events: 4096
  operations-per-tick: 200
  operation-budget-ms: 2.0
  event-batch-size: 128
```

## 項目一覧

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

## YAMLを書くときの注意

階層は空白でインデントし、タブを使いません。
`python:`の下の`executable:`のように、同じ階層を揃えます。
Windowsのパスは上の例のように`/`を使うと、バックスラッシュの解釈を気にせず書けます。
同じキーを重ねて書かず、すでにある項目の値を変更します。

## 負荷に合わせて変える前に

キューの上限を増やすと、保持する作業や通知の数が増えます。
処理能力を増やす設定とは限らないため、まずstatusとログで滞留と欠落を確認します。
移動イベントなど高頻度の通知は[フィルターとrate_limit](events.md)で絞ります。
長い同期処理や外部通信をハンドラーから減らすことも検討してください。

通信フレームのサイズなどは[通信仕様](protocol.md)の固定上限もあります。
この設定だけで任意サイズのデータを送れるようにはなりません。

関連：[反映と運用](operations.md)、[管理コマンド](administration.md)、[Python環境](python-environment.md)。
