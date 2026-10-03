# 困ったとき

[目次](index.md) | 関連：[導入](getting-started.md)、[反映と運用](operations.md)

## 最初に確認する場所

`/pynner status`で`active`を確認し、Paperのconsoleに出た最初のエラーを読みます。
reload要求の受付メッセージは、読み込み成功の通知ではありません。
失敗した候補は旧Runtimeへ影響しないため、以前の機能が動いたままの場合があります。

| 症状 | 確認と対処 |
|---|---|
| `No module named pynner_runtime` | configで指定したPythonへRuntime wheelをインストールする |
| `No module named pynner` | 同じPythonへSDK wheelもインストールする |
| Pythonを起動できない | `python.executable`の絶対パスと実行ファイルの存在を確認する |
| `active=false`のまま | 起動失敗、構文エラー、登録エラーのログを確認する。修正してreload |
| 保存したのに変わらない | `/pynner reload`を実行。auto-reload設定を変えたならPaper再起動 |
| 参加の挨拶が出ない | 読み込み成功後に参加し直す。参加イベントは過去にさかのぼらない |
| 武器IDが見つからない | ファイルがscripts以下か、登録が成功したか、IDの綴りを確認する |
| `Unknown item Material` | Material名と、それがアイテムとして使える種類かを確認する |
| `Entity does not support ...` | Mobのbaseが指定属性を持つかを確認し、対応しない属性を外す |
| `Duplicate ... ID` | 同じIDを登録するクラスが複数ないか確認する |
| `Command is already owned` | 他プラグインと同じコマンド名やaliasを避ける |
| `No permission` | OPまたは管理権限を確認。独自コマンドはそのpermissionを確認 |
| コマンドがconsoleで拒否される | `player_only=True`か、管理用give／spawnを実行していないか確認 |
| `on_load is registration-only` | SDK操作をトップレベルやon_loadからon_enable、ハンドラーへ移す |
| 代入後もHPが古い | 操作完了をawaitしてから`await entity.refresh()`する |
| `on_spawn`で`e.entity`が使えない | Mobのon_spawnはEventではなくEntityを直接受け取る |
| 保護をPython内のifでキャンセルできない | [宣言的キャンセル](events.md#ダイヤブロックを保護する)の対応条件を使う |
| 既存のボスのHPが変わらない | reloadで既存個体の属性を上書きしない。新規spawnで確認 |
| 動いていた関数が呼ばれなくなった | 連続5回の例外による停止を確認。修正してreload |
| 通知が遅れたり抜けたりする | 長い同期処理を避け、移動通知にフィルターを付け、キュー状態を確認 |

## 指定したPythonの環境を調べる

サーバーフォルダーで実行します。
configで別の実行パスを指定した場合は、そのPythonを使います。

```powershell
plugins/Pynner/runtime/venv/Scripts/python.exe --version
plugins/Pynner/runtime/venv/Scripts/python.exe -m pip show pynner pynner-runtime msgpack
plugins/Pynner/runtime/venv/Scripts/python.exe -c "import pynner, pynner_runtime; print(pynner.__file__); print(pynner_runtime.__file__)"
```

このimport確認でゲームへ接続する必要はありません。
SDKとRuntimeが、そのPythonの環境に入っているかを確認するコマンドです。

## 操作のエラーコード

`BridgeError.code`から確認できます。

| コード | 意味 | 対処 |
|---|---|---|
| `ENTITY_GONE` | 個体が死亡、退出、アンロードなどで使えない | 古い参照へ操作し続けない。新しい通知や一覧から取得 |
| `INVALID_ARGUMENT` | 範囲外の値、未知の名前、対象に使えない操作など | メッセージとAPIの引数表を確認 |
| `QUEUE_FULL` | 操作待ちや送信キューの上限 | 処理頻度や大量送信を減らす |
| `TIMEOUT` | 5秒以内に応答しない、または期限内に実行できない | 混雑を確認。副作用のある要求を無条件で再送しない |
| `STALE_GENERATION` | reload前のRuntimeから遅れて届いた要求 | reloadをまたいだ古い処理に依存しない |
| `EXECUTION_ERROR` | 非同期teleportなどの実行失敗 | 原因メッセージ、ワールドと対象の状態を確認 |

「`TIMEOUT`だから処理は行われていない」とは判断できません。
例えば応答を失ったアイテム配布を再送すると、二重配布になる場合があります。

## tracebackの読み方

Runtimeの例外ログには、スクリプトのパスと関数名が含まれます。
末尾の例外型とメッセージから原因を確認し、該当するファイルと行を修正します。

```text
script: events/welcome.py
handler: welcome
...
AttributeError: ...
```

`SyntaxError`は文法、`ImportError`や`ModuleNotFoundError`はimport、`RegistrationError`はIDなどの登録条件を確認します。
登録時にJavaが拒否した場合は、Paper consoleの`Registration failed: ...`も確認してください。

## 今の版で提供しない機能

ゲーム内任意Python console、breakpoint、Pythonからの任意Javaメソッド呼び出しは含みません。
ローカル試験用の[Fabric Debug Mod](fabric-debug.md)は別JARで提供します。
Event Inspector、Foliaの実機対応、独自EntityTypeの追加も含みません。
武器とMobは既存のMaterialとEntityTypeに機能を追加します。
個別機能の検証範囲は[検証記録](verification.md)に記載しています。
