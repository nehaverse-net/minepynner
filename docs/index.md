# Pynner 利用ガイド

Pynnerでは、サーバーに置いたPythonファイルに「参加した人へ挨拶する」「敵を燃やす剣を作る」といった処理を書けます。
まず小さなスクリプトを動かし、追加したい機能のページへ進んでください。
このガイドは同梱のPynner 0.1.0を対象としています。

## 目的に合わせて始める

| 目的 | 最初のページ |
|---|---|
| 起動中のFabricからPythonを試したい | [Fabricクイックスタート](debug-quickstart.md) |
| Paperへ導入してプレイヤーに使ってもらいたい | [Paperへの導入](getting-started.md) |
| 仮想環境なしで使いたい・Pythonの場所を確認したい | [Python環境](python-environment.md) |
| 基礎から機能を作りたい | [順番に作るチュートリアル](tutorial.md) |

仮想環境は任意です。
普段使うPythonへパッケージをインストールして実行できます。
対応バージョンと現在の機能は[要件](requirements.md)、疑問は[よくある質問](faq.md)で確認できます。

## 初めて使うとき

1. [要素の関係を理解する](concepts.md)：JAR、SDK、Runtime、スクリプトの役割。
2. [導入して挨拶を表示する](getting-started.md)：インストール、保存場所、動作確認。
3. [イベントを使う](events.md)：ゲームで起きた出来事へ処理を追加する。

## 作りたい機能から探す

| やりたいこと | 読むページ |
|---|---|
| Fabric 1.21.11で、本番へ移す前にPythonを試す | [FabricデバッグMod](fabric-debug.md) |
| 敵を燃やす剣、回数制限のある武器、クラフトレシピ | [武器とアイテム](weapons.md) |
| 強いゾンビ、独自の装備、報酬付きのボス | [Mob](mobs.md) |
| 参加通知、ブロック保護、チャット監視 | [イベント](events.md) |
| `/restore`のような自分のコマンド | [コマンドと定期実行](commands-and-tasks.md) |
| チェストの選択画面、天候変更、空きマスで整頓 | [チェストGUI・天候・整頓](inventory-gui.md) |
| 回復、移動、メッセージ、アイテム授受 | [操作API](api.md) |
| 保存時の自動反映、起動時の準備、終了時の片付け | [反映と運用](operations.md) |
| 起動しない、反映されない、エラーの意味を知りたい | [困ったとき](troubleshooting.md) |

各ページのコードは、ページで指定した名前の`.py`ファイルへ保存します。
例は必要なものを一つずつ導入してください。
同じIDの武器やMob、同じ名前のコマンドを二つ登録すると、反映に失敗します。

## 手元で迷いやすい用語

| 用語 | 意味 | 例 |
|---|---|---|
| **ID** | 定義をプログラムから指定する名前 | `fire_sword` |
| **Material** | Minecraftに元からあるアイテムやブロックの種類 | `Material.DIAMOND_SWORD` |
| **Entity** | ワールド内のプレイヤー、Mob、矢など | 攻撃されたゾンビ |
| **Player** | Entityのうちプレイヤー専用の操作を持つもの | 参加した人 |
| **イベント** | ゲームで起きた出来事の通知 | `player_join` |
| **ハンドラー** | 通知を受けて実行する関数 | `def welcome(e): ...` |
| **フック** | 武器やMob専用に用意された通知の受け口 | `on_hit` |
| **デコレーター** | 関数やクラスをPynnerへ登録する`@`行 | `@weapon("fire_sword")` |
| **スナップショット** | 通知時点の状態を写した値 | `e.player.health` |
| **PDC** | アイテムやEntityへ保存する追加データ | `{"example:element": "fire"}` |
| **reload** | Pythonを読み直して定義を反映する操作 | `/pynner reload` |

## 運用とリファレンス

[ファイル構成](project-layout.md)、[本番への移行・更新](deployment.md)、[設定一覧](configuration.md)、[管理コマンドと権限](administration.md)を個別に参照できます。
検索付きHTML版の閲覧方法は[ドキュメントの使い方](documentation.md)を参照してください。

## 詳細な技術資料

通常のスクリプト作成には、上の利用ガイドを使ってください。
本体の開発には[内部構成](architecture.md)、[通信仕様](protocol.md)、[検証記録](verification.md)を参照できます。

対応対象はPaper 1.21.11とJava 21以上、Python 3.11以上です。
Windowsの実機で確認しており、LinuxとFoliaの実機検証、負荷試験、PyPI公開は未実施です。

## 他の人へ渡す

[配布ZIPを受け取って使う](sharing.md)に、初回のインストールと実行手順をまとめています。
