# Python環境と仮想環境

Pynnerは仮想環境なしでも動きます。
必要なのは、SDKとRuntimeをインストールしたPythonを、Paperから実行できることです。
Fabricで試す場合は、同じPythonへDebugパッケージも入れます。

## パッケージの役割

| 配布wheel | import・実行名 | 必要な場面 |
|---|---|---|
| `pynner-0.1.0-py3-none-any.whl` | `pynner` | すべてのスクリプト |
| `pynner_runtime-0.1.0-py3-none-any.whl` | `pynner_runtime` | Paperでの実行、Fabricデバッグ |
| `pynner_debug-0.1.0-py3-none-any.whl` | `pynner_debug` | Fabricデバッグ |

Python 3.11以上を使います。
現在の配布はwheelで、PyPI公開は未実施です。
リポジトリのソースを取得しただけの場合は、[ビルド手順](documentation.md#本体の配布物を作る)でwheelを作ります。

## Windowsで普段のPythonを使う

PowerShellで実行します。
このページでは配布物を`D:/pynner`へ展開した例を使います。

```powershell
python --version
python -c "import sys; print(sys.executable)"
python -m pip --version
python -m pip install D:/pynner/dist/pynner-0.1.0-py3-none-any.whl D:/pynner/dist/pynner_runtime-0.1.0-py3-none-any.whl
```

Fabricデバッグも使う場合は続けて実行します。

```powershell
python -m pip install D:/pynner/dist/pynner_debug-0.1.0-py3-none-any.whl
```

`python`が見つからず、Python Launcherがある場合は`py -3.12`などでバージョンを指定できます。
その場合、上のすべての`python`を同じ`py -3.12`へ置き換えます。
複数のPythonがある場合は、途中で別の実行ファイルへ切り替えないでください。

### インストールを確認する

```powershell
python -c "import sys, pynner, pynner_runtime; print(sys.executable); print(pynner.__file__); print(pynner_runtime.__file__)"
python -m pip check
```

パスが表示され、`ModuleNotFoundError`が出なければ、このPythonからパッケージを読み込めます。
Debugの確認には`python -m pynner_debug --help`を使います。

### Paperへ同じPythonを指定する

先ほどの`sys.executable`の出力を、`plugins/Pynner/config.yml`へ設定します。
例えば出力が`C:\Python314\python.exe`なら、次のように書けます。

```yaml
python:
  executable: 'C:/Python314/python.exe'
```

設定後はPaperを再起動します。
PATHにある`python`へ任せるより、絶対パスを指定すると起動元による違いを減らせます。
サーバーをサービスとして動かす場合は、そのサービスの実行ユーザーからもパッケージを読めることを確認してください。

### インストール先へ書き込めない場合

仮想環境の外で、ユーザー領域へインストールできるPythonなら`--user`も使えます。

```powershell
python -m pip install --user D:/pynner/dist/pynner-0.1.0-py3-none-any.whl D:/pynner/dist/pynner_runtime-0.1.0-py3-none-any.whl
```

これはインストールしたユーザー用です。
別ユーザーで起動するサーバーや、ユーザーsite-packagesを無効にしたPythonからは読めない場合があります。
権限エラーを管理者権限だけで解決する前に、実行ユーザーとインストール先を確認します。

## Fabricランチャーが選ぶPython

```powershell
python examples/debug_demo.py
```

Debugランチャーは、自分を実行したPythonの`sys.executable`を試験用Paperへ指定します。
したがって、このコマンドに使ったPythonへ三つのwheelを入れておけば、仮想環境なしで試せます。
IDEの実行ボタンを使う場合も、そのIDEで選んだPythonへ同じパッケージを入れます。

## 必要なら仮想環境を使う

複数サーバーで依存パッケージの版を変えたい場合や、普段のPythonと分けたい場合に使います。
Pynnerの必須条件ではありません。

```powershell
py -3.12 -m venv D:/minecraft-server/plugins/Pynner/runtime/venv
D:/minecraft-server/plugins/Pynner/runtime/venv/Scripts/python.exe -m pip install D:/pynner/dist/pynner-0.1.0-py3-none-any.whl D:/pynner/dist/pynner_runtime-0.1.0-py3-none-any.whl
```

`python.executable`へこのvenvの`Scripts/python.exe`を指定します。
有効化コマンドを実行しなくても、実行ファイルを直接指定すれば動きます。

## LinuxのPython

自分で管理するPythonなら、同じように`python3 -m pip install ...`で導入し、その実行ファイルの絶対パスを指定できます。
ディストリビューション管理のPythonでは、pipが`externally-managed-environment`として変更を拒否する場合があります。
その場合はOSのPythonを強制変更せず、別のPythonまたはvenvを使います。
Linux手順は用意していますが、Pynnerの実機検証はWindowsのみです。

## よくある取り違え

| 症状 | 確認すること |
|---|---|
| pip install成功後もimportできない | `pip`単独ではなく、実行するPythonの`-m pip`を使ったか |
| ターミナルでは動くがPaperで失敗 | `config.yml`が別のPythonや古いvenvを指していないか |
| IDEだけ失敗 | IDEのインタープリター選択がターミナルと同じか |
| Debugモジュールが見つからない | Debug wheelも同じPythonへ入れたか |
| wheelの同じ版を更新できない | 更新時に`--force-reinstall`を使ったか |

関連：[導入](getting-started.md)、[Fabricデバッグ](debug-quickstart.md)、[困ったとき](troubleshooting.md)。
