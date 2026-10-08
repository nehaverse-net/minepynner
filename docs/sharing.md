# 配布ZIPを受け取って使う

PynnerをPyPIへ公開する必要はありません。
配布ZIPにはPythonの三つのwheel、Paperプラグイン、Fabric Debug Mod、サンプルをまとめています。
受け取る人はソースをビルドせずに導入できます。

[配布ZIPをダウンロード](https://nehaverse-net.github.io/minepynner/downloads/pynner-0.1.0-release.zip)

## 渡す人が用意するもの

`pynner-0.1.0-release.zip`を相手へ渡し、このページのURLも伝えます。
作業フォルダー全体や自分の`.pynner`フォルダー、デバッグワールド、Pythonの仮想環境は渡しません。
ローカルのPython設定や認証情報も必要ありません。
自分が作ったスクリプトを試してもらう場合は、使いたい`.py`も別途渡します。

## 受け取った人が一度だけ行うこと

1. ZIPを好きなフォルダーへすべて展開します。ZIPの中で直接実行しないでください。
2. Python 3.11以上を導入します。Javaは21以上、対象Minecraftは1.21.11です。
3. Windowsでは展開先の`install.bat`をダブルクリックします。管理者として実行する必要はありません。
4. インストール完了の表示を確認します。

Pythonがすでにある場合、コマンドでも同じ導入ができます。
展開先で実行してください。

```shell
python install.py
```

この作業はSDK、Runtime、Debugを同じPythonへ入れます。
Pynner本体は同梱wheelから導入します。
MessagePackなどの依存ライブラリはpipが通常のパッケージ配布元から取得するため、初回導入にはインターネット接続が必要です。
完全にネット接続なしで導入するためのZIPではありません。
仮想環境は任意です。

以前の配布名のパッケージが入っている場合、installerは上書きせず停止します。
別の作者の同名パッケージを勝手に削除しないためです。
[旧配布名からの更新手順](publishing.md#旧配布名から更新する)を確認してください。

## 自分のMinecraftでPythonを試す

1. Fabric版Minecraft 1.21.11にFabric APIと`dist/pynner-debug-fabric-0.1.0.jar`を入れます。
2. Paper 1.21.11のサーバーJARを公式配布元から用意します。Paper本体はZIPに含めません。
3. 使う人のPCでPaperとJavaの場所を登録します。

```shell
python -m pynner_debug configure --paper-jar "自分のPaperのパス/server.jar" --java "自分のJavaのパス/java.exe"
```

上のパスは自分のPCの実際の場所に置き換えます。
Minecraft EULAへの同意は各利用者が確認してください。
同意済みの`eula.txt`がない場合、同意後に`--accept-eula`を指定します。
こちらで同意を代行した状態のPaper設定は配布しません。

Mod入りMinecraftを起動してタイトル画面で待ち、試すPythonを実行します。
配布サンプルなら次の例で天候メニューとチェストの整頓を試せます。

```shell
python examples/weather_chest.py
```

接続後、`/cher`を実行します。
Pythonを保存すると自動で反映され、Ctrl+Cで試験セッションが終了します。
詳しい手順は[Fabricで最初のPythonを実行する](debug-quickstart.md)にあります。

## Paperサーバーで使う

サーバー側では`dist/pynner-paper-0.1.0.jar`を`plugins/`へ置きます。
サーバーを動かすPCのPythonへパッケージを導入し、`python.executable`を設定します。
自作スクリプトは`plugins/Pynner/scripts/`へ配置します。
本番サーバーへ参加する通常のプレイヤーはDebug Modを入れなくても使えます。
[Paperへの導入手順](getting-started.md)を参照してください。
