# Phase 1の構成と実装段階

JavaはPaperとネイティブな武器／Mob定義を所有する。
CPythonは通常のPythonスクリプトを実行する。
Javaオブジェクトを共有せず、スナップショットと列挙された操作をTCPで渡す。

## IPC選定

| 候補 | 採用判断 |
|---|---|
| localhost TCP | WindowsとLinuxで同じ接続方式を使えるため採用 |
| Unix Domain Socket | 将来のtransport差し替え候補 |
| gRPC | schema生成と配布依存の追加を初期版では見送る |
| WebSocket | 将来のブラウザー診断画面用候補 |
| HTTP JSON | イベントごとの要求には採用しない |
| MessagePack | TCPフレームのデータ形式に採用 |

性能の選定はベンチマークによる最速保証ではない。
イベント購読、フィルター、バッチ、有限キュー、優先度によって処理量を制御する。

## モジュールと主要クラス

| モジュール | 実装 |
|---|---|
| pynner-protocol | `Frames`、長さ上限、型と深さの検証、フレームテスト |
| pynner-paper | `PynnerPlugin`、初回ディレクトリ生成、管理コマンド |
| bridge | `RuntimeSupervisor`、`RuntimeSession`、`Operations` |
| scheduling | `ExecutionRouter`、Entity／Region／Global／Asyncの振り分け |
| events | `EventBridge`、`NativeHooks`、`Snapshots` |
| definitions | `Definitions`、登録検証、recipeとtaskの所有管理 |
| items | `Items`、ItemMetaとPDC、属性、recipe |
| commands | `PythonCommands`、public CommandMapアダプター |
| pynner-runtime | `Worker`、`ScriptLoader`、transport |
| pynner-sdk | 型付き参照、dataclass、decorator、Bridge Protocol、TypedDict |

Javaの登録切替はPaperのtick側で行う。
ソケット、プロセス、ファイル監視はvirtual threadで行う。
Pythonからの操作は上限付きキューへ入り、実行直前にも世代と期限を検証する。
Entityの変更はEntityScheduler、座標を対象にした変更はRegionSchedulerへ送る。
Folia対応には、残るGlobal上の一覧取得などをregion所有権に合わせて検証する必要がある。

## 12段階の成果物

| 段階 | 成果物 |
|---|---|
| 1 要件整理 | READMEの対応環境、操作契約、制限 |
| 2 アーキテクチャ | 本書、Java/Pythonの責務分離 |
| 3 IPC比較 | 本書の選定表、2系統のTCP接続 |
| 4 モジュール | Mavenの2モジュール、Pythonの2パッケージ |
| 5 API | player/entity/item/weapon/mob/world/event/command/scheduler/server |
| 6 プロトコル | protocol.md、Frames、Python transport |
| 7 最小PoC | examples/hello.py、TCP worker統合テスト |
| 8 Weapon | Items、NativeHooks、recipe、PDC、各フック |
| 9 Mob | Definitions、属性検証、spawn、drop、各フック |
| 10 Events | 13型、購読、Javaルール、フィルター、優先キュー |
| 11 Commands | 引数型、aliases、権限、console、補完、Scheduler |
| 12 Hot Reload | 候補Runtime、登録検証、世代切替、watch、復旧 |

## reloadの契約

候補を新しいプロセスでロードし、on_loadを実行してから登録を検証する。
検証に失敗した候補は有効化しない。
登録の適用中に失敗した場合は、直前の登録を再適用する。
成功時は新世代を有効化し、旧Runtimeへon_disableを通知する。
通常のPython import、C拡張、ユーザーthreadを確実に再作成するため、Runtime全体を交換する。
同じファイルのimportlib.reloadだけで状態を引き継ぐ方式は採用しない。

登録の巻き戻しと、任意Pythonコードによる外部副作用の巻き戻しは異なる。
候補スクリプトが通常のPythonとして実行したファイル操作などは巻き戻せない。

## 拡張時の方針

新しい操作はOperationsの明示的な分岐とSDKメソッドへ追加する。
引数の検証、対象Scheduler、成功結果、エラーコードを通信契約へ追記する。
Javaの任意メソッドを反射で呼び出すAPIにはしない。
新しいイベントは型、スナップショット変換、購読名を追加する。
今後破壊的なプロトコル変更が必要ならversionを更新して接続時に拒否する。

将来のDebug Modには、定義一覧、イベント一覧、エラー、Entity状態を読む診断APIを追加する。
VS Code保存時のreload、Event Inspector、Error OverlayはそのAPIから利用できる。
最初のPaper実装後に、別モジュールのFabric 1.21.11向けDebug ModとPythonランチャーを追加した。
Debug Modは認証付きlocalhost制御を受け、Pythonランチャーが管理する試験専用Paperへ自動接続する。
Paperの実装を使うため、独自のMinecraft内部サーバー向けAPIを再実装しない。
ゲーム内任意Python Console、breakpoint機構は含めない。
