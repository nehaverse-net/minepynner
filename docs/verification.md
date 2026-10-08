# 検証記録

## PyPI向けの配布準備

2026-10-08、三つの配布名をminepynner、minepynner-runtime、minepynner-debugへ変更しました。
import名は維持しています。
README、Apache-2.0ライセンス、配布先リンク、SDKのserverとdebug extraを追加しました。

- 三つのsdistを作り、それぞれのsdistからwheelをビルド。六つの配布物すべてがtwine check --strictに合格。
- 新しいPython 3.14の環境に、ローカルwheelからminepynner[debug]を導入。三つの依存解決、pip check、Pythonテスト64件、RuntimeとDebugのCLI helpが成功。
- wheel内のライセンス、SDKの型情報、Debug内のプラグインJARと元JARの一致を確認。sdistにREADMEとLICENSEが含まれることも確認。
- GitHub Actionsの設定はYAMLとして解析。通常pushでは公開せず、手動指定時だけTrusted Publishingを行う構成を確認。

この時点ではTestPyPIとPyPIには公開していません。
Trusted Publisherの登録には各サービスへのログインが必要です。
GitHub ActionsのPython 3.11とJava 21によるビルドは、GitHub上の実行結果で別途確認してください。

## 二次元配列のGUI配置

2026-10-08、Pythonテスト64件、型検査35ファイル、lintが成功しました。
実際のPaper 1.21.11へクライアントを接続し、3行の配列から27マスのGUIが開き、中央（行1、列4）と右下（行2、列8）に指定したアイテムが配置されることを確認しました。
中央をクリックし、専用プロパティのrowとcolumnで通知を判定できることも確認しました。
従来の辞書形式、GUIの更新と切り替え、天候変更、コマンド入力制限、通常チェストの整頓も同じ接続で確認しました。
不揃いな行、空配列、7段、サイズの不一致、不正なセルはPython側で拒否します。

## イベントの専用プロパティとGUI更新、コマンド入力制限

2026-10-08、Windows、Paper 1.21.11、Java 23で検証しました。

- Pythonテスト54件、Javaテスト12件が成功。型検査35ファイルとlintも成功。
- イベントの本文、座標、アイテムを専用プロパティとして取得し、以前のdata辞書との互換性を確認。
- `tools/gui_smoke.py`で実際のPaperにMineflayerを接続し、開いた画面のページ更新、確認画面への切り替え、決定とキャンセルを確認。
- コマンドの選択肢をTab候補で取得し、不足引数、選択肢外、型変換失敗、数値の上下限外、過剰な引数で指定した日本語の文面が表示されることを確認。
- Javaの単体テストで型の変換、数値の選択肢、上下限を含む範囲、NaNの拒否、引数専用エラー文の優先、入力文字列を再展開しないことを確認。
- 不正なスロットを含む更新が部分適用されないこと、Noneによる削除とclearによる全置換を確認。
- 切り替え前のview_idで新しい画面を更新する要求を拒否。従来の天候GUIとチェストの整頓も同じ接続で確認。

専用プロパティと新しい引数制限はSDKの更新が必要です。
GUI更新とコマンド入力制限は、PaperプラグインのJARも同時に更新してください。

## チェストGUI・天候変更・整頓

2026-10-05、Windows、Paper 1.21.11、Java 23で検証しました。

- Pythonテスト40件、Javaテスト9件が成功。型チェック34ファイルとlintも成功。
- 独立した試験サーバーへMineflayerクライアントを接続し、`/cher`でGUIが開くこと、二つのボタンで雨と晴れに変わり、画面が閉じることを確認。
- GUIのShiftクリックでアイテムを持ち出せないことを確認。
- 通常のチェストの空きマスを左クリックすると、同種のスタックがまとまり、Material順に並ぶことを確認。合計78個の個数と名前付きアイテムの情報が保持されました。
- カーソルにアイテムがある状態と、画面を閉じた後の古いview_idによる整頓要求を拒否することを確認。

再実行は`.venv/Scripts/python.exe tools/gui_smoke.py`です。
既存のDebug設定を使って独立した試験サーバーを作り、試験後に正常終了します。
他プラグインによるインベントリー操作との全組み合わせやFolia実機は未検証です。

## 死亡中プレイヤーへのチャット送信

2026-10-05、Windows、Paper 1.21.11、Java 23で検証しました。
`player.send_message`だけは、オンラインのPlayerが死亡により`isValid=false`になっていても実行できるように修正しました。
その他のEntity操作の有効性チェックは維持しています。

`tools/chat_filter_smoke.py`で、クライアントの自動リスポーンを無効にし、`/kill @s`による死亡通知からメッセージを送信しました。
体力が0以下のまま送信者だけが`DEATH_NOTICE`を受信し、相手には届かないことを確認しました。
同じ実行でチャット本文フィルターの確認も成功しました。

## チャット本文フィルター

2026-10-05、Windows、Paper 1.21.11、Java 23で検証しました。

- Pythonテスト39件、Javaテスト9件が成功。SDKの型チェックとlintも成功。
- 日本語の禁止語二つを指定し、実際のPaperへ二つのローカルMineflayerクライアントを接続。
- 普通の発言が相手へ届くこと、どちらの禁止語を含む発言も相手へ届かないこと、注意文が送信者だけへ二回届くことを確認。
- 空の禁止語や不正な型、チャット以外への本文条件指定を拒否。部分一致を正規表現として扱わず、大文字と小文字を区別することもテスト。

再実行は`.venv/Scripts/python.exe tools/chat_filter_smoke.py`です。
既存のDebug設定のPaper JARとJavaを使い、独立した試験フォルダーで動かして終了します。
事前にMavenビルドのJARを`dist/pynner-paper-0.1.0.jar`へ配置し、`tools/client`のNode依存を導入します。

## ドキュメントサイトと仮想環境なしの確認

2026-10-03、Windowsで次を確認しました。

- MkDocs 1.6.1、Material 9.7.7で`mkdocs build --strict`が成功。
- 24ページの本文と生成された404ページの内部リンク1,453件について、リンク先ファイルと見出しの存在を確認。
- ドキュメント内のPythonコード例33件を構文解析し、構文エラーがないことを確認。ゲーム内の全コード例を今回改めて実行した記録ではありません。
- ブラウザーの狭い画面とデスクトップ幅で表示を確認。日本語の「仮想環境」で検索結果を確認。
- HTML配布ZIPの74ファイル、検索データとindex.htmlの格納、ZIP整合性を確認。
- venv外の`C:/Python314/python.exe`で、`sys.prefix == sys.base_prefix`を確認し、配布wheelのSDK・Runtime・Debugをimport。Debug CLIのhelpも起動。

最後の確認では、普段のsite-packagesを変更せず、wheelを作業用フォルダーへpipの`--target`で入れて`PYTHONPATH`から読み込みました。
通常のPython実行ファイルでパッケージが動くことの確認であり、今回のチェックで新しい試験ワールドを起動したわけではありません。
PaperとFabricを使った実動作の記録は以下を参照してください。

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
