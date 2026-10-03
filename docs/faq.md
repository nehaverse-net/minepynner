# よくある質問

## 仮想環境なしでも動く？

動きます。
普段使うPythonへSDKとRuntimeをインストールし、Paperの`python.executable`へその実行ファイルを指定します。
FabricではDebug wheelも同じPythonへ入れ、`python examples/debug_demo.py`で実行できます。
実際のコマンドと確認方法は[Python環境](python-environment.md)にあります。

## Pythonを実行するだけで試験ワールドへ入れる？

Mod入りのFabric 1.21.11をタイトル画面で起動しておけば、DebugランチャーがPaperを起動して自動接続します。
ファイル末尾へ`pynner.debug.run`の起動コードを書くか、`python -m pynner_debug ファイル.py`で指定します。
Minecraft自体の起動はランチャーで行います。
すでに別のワールドに入っている場合は、タイトルへ戻してください。

## シングルプレイのワールドを使う？

試験専用のPaperと新しいflatワールドを作ります。
Minecraftから見るとlocalhostへのマルチプレイ接続です。
既存のシングルプレイワールドや本番ワールドは自動で開きません。

## 本番のプレイヤーもModが必要？

Pynnerの追加機能を本番Paperで使うためのクライアントModは不要です。
Debug Modは、開発者の自動接続と状態表示用です。
独自テクスチャなどのクライアント表現は、このModの機能ではありません。

## BukkitやPaperのAPIを全部Pythonから呼べる？

SDKが用意した操作を使います。
JavaオブジェクトをPythonへ直接渡す方式ではありません。
公開する型とメソッドは[操作API](api.md)を参照してください。

## event内でcancelledを書き換えれば保護できる？

Pythonの通知は読み取り専用です。
即時キャンセルが必要な処理はJava側の宣言ルールへ登録します。
書き方と対応条件は[イベント](events.md)にあります。

## 保存すれば反映される？

本番の既定は手動の`/pynner reload`です。
`runtime.auto-reload: true`で保存時の監視を有効にできます。
Fabricの試験用サーバーでは自動反映を有効にします。
設定ファイルやpipパッケージの更新は、Pythonスクリプトのreloadとは区別します。

## Pythonの変数はreload後も残る？

新しいプロセスで全スクリプトを読み直すので、通常の変数やクラスのインスタンスは作り直されます。
個体へ残すデータはPDCなど、用途に応じた保存を使います。
武器やMobの定義クラスの`self`は、種類ごとに共有される点にも注意してください。

## 別のPythonライブラリも使える？

Pynnerを実行するPythonへpipで入れればimportできます。
サーバーのPythonとエディターのPythonが異なる場合は、それぞれで必要な依存を揃えます。
長い処理やブロッキングI/Oはハンドラー全体を遅らせるため、イベント処理に長時間待つ操作を直接置かない設計にします。
スクリプトはサーバーと同じOSユーザー権限で動きます。

## F8でブレークポイントを設定できる？

現在のDebug画面は状態、エラー、最近のログとreloadを提供します。
ブレークポイント、行ごとのステップ実行、Entityやイベントの専用Inspectorは未実装です。

## 対応バージョンを変えられる？

この版はPaperとFabricのMinecraft 1.21.11を対象にしています。
設定の番号を変えるだけで別バージョンへ対応するものではありません。
対応と検証の範囲は[要件](requirements.md)と[検証記録](verification.md)で確認できます。
