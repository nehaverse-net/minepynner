# Fabricで最初のPythonを実行する

このページの目標は、起動済みのMinecraftから試験ワールドへ入り、Pythonで作った剣とMobを動かすことです。
PowerShellを使い、配布物の展開先を`D:/pynner`とします。
異なる保存先を使う場合はパスを置き換えてください。

## 1. 必要なファイルを揃える

| ファイル・環境 | 用途 |
|---|---|
| Fabric版Minecraft 1.21.11 + Fabric API | 開発用クライアント |
| `dist/pynner-debug-fabric-0.1.0.jar` | Minecraft側の接続・状態表示 |
| `dist/`のSDK・Runtime・Debug wheel | Python側の実行と試験サーバー起動 |
| Paper 1.21.11のJAR | 試験サーバー本体 |
| Java 21以上、Python 3.11以上 | 実行環境 |

ソースだけを取得した場合、`dist/`はビルドで生成します。
手順は[配布物のビルド](documentation.md#本体の配布物を作る)にあります。

## 2. Modを入れる

使用するMinecraftインスタンスの`mods/`へFabric APIとDebug Modを置きます。
公式ランチャーの既定ゲームディレクトリでは、Windowsの場所は`%APPDATA%/.minecraft/mods/`です。
インスタンスの場所を変更している場合は、その場所を使います。

Minecraftを起動し、タイトル画面で待ちます。
F8を押してPynner Debug画面が出れば、Modを読み込めています。
すでにワールドに入っている場合はタイトルへ戻ります。

## 3. 普段のPythonへインストールする

仮想環境は必要ありません。

```powershell
Set-Location D:/pynner
python --version
python -m pip install dist/pynner-0.1.0-py3-none-any.whl dist/pynner_runtime-0.1.0-py3-none-any.whl dist/pynner_debug-0.1.0-py3-none-any.whl
python -m pynner_debug --help
```

以後もこのPythonを使います。
別のPythonやIDEへ切り替える場合は、[Python環境の確認](python-environment.md)を行います。

## 4. PaperとJavaの場所を登録する

例えばPaper JARを`D:/paper-1.21.11/server.jar`へ置いた場合です。
Javaのパスは自分の環境へ置き換えます。
`--accept-eula`は[Minecraft EULA](https://aka.ms/MinecraftEULA)へ同意した場合に指定します。

```powershell
python -m pynner_debug configure --paper-jar D:/paper-1.21.11/server.jar --java 'C:/Program Files/Java/jdk-21/bin/java.exe' --accept-eula
```

JARと同じフォルダーの`eula.txt`にすでに`eula=true`がある場合は、EULA引数を省けます。
設定はユーザーの`~/.pynner/debug/config.json`へ保存されるので、毎回指定する必要はありません。

## 5. Minecraftが見つかるか確認する

```powershell
python -m pynner_debug clients
```

起動中のMod入りクライアントが表示されることを確認します。
複数ある場合は、[クライアント選択](fabric-debug.md)の`--client`を使います。
見つからない場合は、ゲームバージョン、Modの保存先、クライアント起動を順に確認してください。

## 6. サンプルを起動する

```powershell
python examples/debug_demo.py
```

ランチャーが新しい試験用Paperを起動し、Pythonの読み込み完了を待ってMinecraftへ接続を要求します。
Minecraftが自動でflatの試験ワールドへ入ります。
初回はPaperのダウンロードと生成に時間がかかることがあります。

この接続は、内部的にはlocalhostのPaperサーバーへのマルチプレイ接続です。
元のPaperサーバーのワールドやpluginsを使わず、試験用フォルダーへ新しいワールドを作ります。

## 7. ゲーム内で確認する

```text
/pynner status
/pynner give debug_blade
/pynner spawn debug_target
```

`active=true`と参加時メッセージを確認します。
Debug Bladeで練習用ゾンビを攻撃すると炎上します。
Creativeで戦闘を確認しにくい場合は`/gamemode survival`へ切り替えます。

`/debugerror`はサンプルが意図的に例外を起こすコマンドです。
F8の画面とPythonのターミナルにエラーが表示されることを確認できます。

## 8. 編集して保存する

`examples/debug_demo.py`の剣の`name`や`damage`を変更し、保存します。
自動reloadが完了したら、`/pynner give debug_blade`で新しい剣を取得して確認します。
既存の所持アイテムの表示がすべて即時更新されることを前提にしないでください。

構文エラーがある変更は有効にならず、読み込み済みの旧世代を維持します。
修正して保存し、世代の更新とエラー解消を確認します。

## 9. 終了する

PythonのターミナルでCtrl+Cを押します。
Minecraftはタイトルへ戻り、Paperはワールドを保存して停止します。
Minecraft自体は起動したままです。

試験フォルダーは`~/.pynner/debug/sessions/`以下に残ります。
同じワールドへの再接続を保存データの管理機能として提供する版ではありません。
詳しい保存場所、CLI、エラーの意味は[Fabricデバッグ詳細](fabric-debug.md)を参照してください。

次は[機能を順番に作るチュートリアル](tutorial.md)へ進めます。
