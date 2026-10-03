# できることと対応環境

Pynner 0.1.0は、PaperサーバーへPythonファイルで機能を追加するフレームワークです。
ゲームへの操作はJavaプラグインが担当し、Pythonは別プロセスで動きます。

## 二つの使い方

| 目的 | 用意するもの | 最初に読むページ |
|---|---|---|
| 手元のMinecraftで作って試す | Fabricクライアント、Debug Mod、Paper JAR、Pythonの三つのwheel | [Fabricクイックスタート](debug-quickstart.md) |
| サーバーでみんなに使ってもらう | Paper、Pynner Paper JAR、PythonのSDK・Runtime wheel | [Paperへの導入](getting-started.md) |

本番のプレイヤーはPynnerのクライアントModを入れずに接続できます。
Debug Modは開発者が自分のクライアントで使うためのものです。

## 要件

| 要素 | 対象 |
|---|---|
| Minecraft / Paper | 1.21.11 |
| Java | 21以上。成果物はJava 21のclassfile |
| Python | CPython 3.11以上 |
| Pythonの仮想環境 | 任意。普段のPythonへインストール可能 |
| Debugクライアント | Fabric版Minecraft 1.21.11 |
| Fabric Loader | 0.18.4以上。検証では0.19.5 |
| Fabric API | 1.21.11向け。検証では0.141.6+1.21.11 |

PaperのJARは配布物に含めません。
[Paper公式の導入ガイド](https://docs.papermc.io/paper/getting-started/)を参考に、対象バージョンを用意してください。
別のMinecraftバージョン向けのJARを名前だけ変えて使うことはできません。

## 現在作れる機能

| 機能 | 具体例 | 詳細 |
|---|---|---|
| イベント | 参加時の挨拶、移動通知、ブロック保護 | [イベント](events.md) |
| 武器 | 命中で炎上、使用回数、独自名、レシピ | [武器](weapons.md) |
| Mob | 体力・装備・ドロップ・AI・専用フック | [Mob](mobs.md) |
| コマンド | 型付き引数、権限、別名、Tab補完 | [コマンド](commands-and-tasks.md) |
| 定期処理 | お知らせ、一回だけの遅延処理 | [定期実行](commands-and-tasks.md) |
| Minecraft操作 | 回復、移動、アイテム授受、Mob生成 | [操作API](api.md) |
| 開発支援 | 保存時のreload、型補完、エラー表示 | [運用](operations.md)、[Debug](fabric-debug.md) |

## 実装上の境界

Pythonのイベントは、発生後の読み取り専用通知です。
`e.cancelled = True`のような書き換えでPaperのイベントを取り消す方式ではありません。
即時キャンセルが必要な機能は、[Java側で評価する宣言ルール](events.md)を使います。

クラス定義は武器・Mobの種類ごとに一つのPythonインスタンスを共有します。
個々の剣やMobの状態を`self`へ保存すると、別の個体と混ざります。
個体の識別と状態はUUIDやPDCを使って管理します。

SDKの値は通知時点のスナップショットです。
現在の状態が必要な場合は`await entity.refresh()`を使います。
SDKは任意のBukkit APIやJavaオブジェクトを直接公開していません。

## 検証した範囲

Windows上でPaper 1.21.11、Python 3.14と3.12、Fabricの開発クライアントを使って検証しています。
Linux・Foliaの実機動作、大規模サーバーのTPS、他のModすべてとの組み合わせは未検証です。
具体的な実行記録は[検証記録](verification.md)を参照してください。
