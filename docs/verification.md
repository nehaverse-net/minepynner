# 検証記録

## Fabric Debugの検証

2026-10-03、Windows、Java 23、Minecraft 1.21.11、Fabric Loader 0.19.5、Fabric API 0.141.6+1.21.11で確認しました。
ModはJava 21向けへコンパイルしています。

- Fabric ModのGradle buildとremapJarが成功。
- Pythonの自動テスト32件と型検査32ファイルが成功。既存JavaのJUnit 6件も成功。
- Fabricの開発クライアントを実際に起動し、Pythonサンプルの直接実行から試験用Paperへ自動接続。
- Pythonの参加メッセージをMinecraftのチャットで確認。
- Modから送ったコマンドで武器とMobを生成し、Pythonコマンドの意図的な例外を取得。
- 例外をModの状態へ転送し、デバッグ画面の描画中もクライアントが稼働。
- 認証なしの制御HTTP要求は401で拒否。接続済みクライアントへの再接続要求も拒否。
- 保存時にgeneration 2へ更新。構文エラーのgeneration 3を拒否し、2を維持。修正後に4へ更新。
- 通常終了でModを切断し、Paperを正常終了。
- IDEの停止を模したPythonプロセス強制終了で、Paperの親監視が作動し、ワールド保存と停止を確認。

統合検証コードは`tools/fabric_debug_smoke.py`、強制終了検証は`tools/debug_parent_smoke.py`です。
他Modとの全組み合わせ、Linux実機、breakpoint機能は確認対象ではありません。
エラー表示の受信と画面描画は確認していますが、スクリーンショットによる見た目の検査は行っていません。

## 利用ガイドの検証

2026-10-03、利用ガイドを10ページ追加しました。
文書内のローカルリンク44個の参照先を確認し、Pythonコードブロック27個をコンパイルしました。
そのうち単独で読み込める例19個をSDKで実行し、登録時の例外がないことを確認しました。
この確認ではゲーム操作を実行していません。
以下のPaper実機確認は、利用ガイド追加前の本体実装の検証です。

## 本体の検証環境

2026-10-03、Windows上で実施しました。
対象はPaper 1.21.11 build 111、実機のJavaは23、コンパイル先はJava 21です。
実機RuntimeはCPython 3.14、配布wheelの独立環境はCPython 3.12です。

## 自動検証

- `mvn -B package`: 成功。MessagePackフレームのJUnitテスト6件が成功。
- `python -m pytest -q`: 21件成功。SDK、ローダー、フレーム検証、認証付き2接続のRuntime統合を検証。
- CPython 3.12の独立venvへwheelをインストールし、同じ21件とRuntime CLI起動を確認。
- `mypy pynner-sdk/src pynner-runtime/src examples`: 26ファイルでエラーなし。
- `ruff check pynner-sdk/src pynner-runtime/src examples tests`: 成功。

## Paperとクライアントによる確認

既存サーバーの許諾済みPaperバイナリを専用の `dev-server/` へ複製し、新規ワールドで検証しました。
検証サーバーはlocalhost限定、ポート25591です。
元サーバーの設定とワールドは変更していません。
Mineflayerの検証コードは `tools/client/` に保存しています。

- Hello Worldのログイン通知、Pythonコマンド、型付き引数、console、alias、give、spawn。
- 全13イベントの通知。ログイン、退出、移動、操作、ダメージ2種、死亡、spawn、破壊、設置、inventory click、projectile hit、chat。
- 武器のItemMeta、PDC、攻撃属性、耐久設定と、装備、装備解除、右クリック、命中、撃破、使用回数による破損の通知。
- Mobのspawn、ダメージ、死亡、操作の通知。
- Entityのdamage、heal、fire、teleport、effect、PDC、killと操作完了応答。
- ダイヤブロック破壊の宣言的キャンセル。クライアント表示だけでなく、サーバー側のblock状態を確認。
- 手動reload後のコマンド再登録。PaperのCommandMapに対応し、重複と解除例外を修正。
- ファイル変更のHot Reload。Python構文エラーと不正Materialの候補を拒否し、旧世代のコマンド実行が継続。
- Pythonハンドラーの意図的な例外後にも、別コマンドが正常動作。
- Pythonプロセスの強制終了中もPaperのversionコマンドが動作。約5秒後のRuntime自動復旧と復旧後のコマンドを確認。
- サーバー停止時のon_disable実行と正常終了を確認。

各武器・Mobフックのすべての組み合わせを実機で網羅したわけではありません。
大量Mob・多数プレイヤーのTPSベンチマーク、長時間耐久試験、Linux実機、Folia実機は未検証です。
SDKとRuntimeはローカルwheelとして提供し、PyPIへは未公開です。
検証専用スクリプトには強制終了用コマンドが含まれるため、本番のscriptsへコピーしないでください。
