# 更新と本番への移行

Fabricで確認したPythonを、本番Paperへ移す手順です。
本番にはDebug Modを配布せず、Pynner Paper JARとSDK・Runtimeを導入します。

## 移行前に確認する

1. 本番もPaper 1.21.11で、SDK・Runtime・JARの版が揃っていることを確認します。
2. `/debugerror`など試験専用の機能を外します。
3. コマンドの権限を決め、一般プレイヤーからの実行も確認します。
4. `localhost`専用の試験サーバー設定を本番へコピーしないようにします。
5. 現在のscripts、configとワールドをバックアップします。

Debug環境はCreative・OP・一人接続です。
その状態だけで、本番の権限、インベントリ不足、複数人の操作をすべて確認できるわけではありません。

## スクリプトを配置する

作業フォルダーの必要な`.py`と補助ファイルを、本番の`plugins/Pynner/scripts/`へコピーします。
複数ファイルの相対importを使っている場合は、フォルダー構成も維持します。
本番ですでに同じIDやコマンド名が使われていないことを確認してください。

```text
my-project/weapons/fire_sword.py
  → plugins/Pynner/scripts/weapons/fire_sword.py
```

`if __name__ == "__main__": run(__file__)`は残しても、本番のRuntimeから読み込んだときには実行されません。
本番用PythonにDebug wheelを入れる必要もありません。

## 反映と確認

consoleから実行します。

```text
pynner reload
pynner status
```

reload要求の受付後、`Activated Python generation ...`と`active=true`を確認します。
武器とMobの定義数、追加したコマンド、一般プレイヤーの権限を確認します。
新しいアイテムを取得して効果を試し、ログにPython例外が出ていないか確認します。

構文・登録エラーでは旧世代を維持します。
有効化後にコードが外部ファイルやゲーム状態を変更した結果まで、自動で元に戻すものではありません。

## wheelとJARを更新する

PythonパッケージやJava JARを変更するときは、Paperを停止してから更新します。
サーバーで使っているPythonを指定してください。
同じ0.1.0という版番号のwheelを置き換える場合も、`--force-reinstall`で入れ直せます。

```powershell
python -m pip install --force-reinstall D:/pynner/dist/minepynner-0.1.0-py3-none-any.whl D:/pynner/dist/minepynner_runtime-0.1.0-py3-none-any.whl
python -m pip check
```

1. `plugins/`のPynner JARを更新します。
2. `python.executable`が更新したPythonを指しているか確認します。
3. Paperを起動します。
4. Runtime有効化と追加機能の動作を確認します。

pip依存パッケージの更新は、Pythonを共有する他のプロジェクトにも影響する場合があります。
版を別々に管理したい場合は[任意の仮想環境](python-environment.md#必要なら仮想環境を使う)を使います。

## 変更を戻す

スクリプトだけの変更なら、バックアップしたファイルへ戻してreloadします。
削除・追加したファイルも含めて元の構成へ戻します。
JARやwheelも戻す場合は、Paperを止めて対応する版へ揃えてから再起動します。
PDCやワールドへ保存済みのデータは、Pythonファイルを戻すだけで消えたり復元されたりはしません。

関連：[反映と運用](operations.md)、[設定](configuration.md)、[困ったとき](troubleshooting.md)。
