# PyPIへの公開準備と手順

## 配布名とimport名

PyPIの`pynner`は別の作者のプロジェクトが使用しています。
このプロジェクトは次の配布名を使い、Pythonのimport名を維持します。
2026-10-08のPyPI JSON APIでは、三つの配布名は未登録でした。
この確認で名前を予約したわけではありません。

| PyPIの配布名 | Pythonのimport名 | 用途 |
|---|---|---|
| `minepynner` | `pynner` | SDK |
| `minepynner-runtime` | `pynner_runtime` | サーバーのPython worker |
| `minepynner-debug` | `pynner_debug` | ローカルのデバッグ起動 |

初回公開後は、次のように導入できます。

```shell
python -m pip install minepynner
python -m pip install "minepynner[server]"
python -m pip install "minepynner[debug]"
```

公開するまで、このコマンドで導入できるとは案内しないでください。
PaperプラグインとFabric ModはPythonのpipだけでは導入されません。

## 配布物を作る

SDK、Runtime、Debugはすべて同じ版を使います。
各パッケージの`pyproject.toml`に説明、README、Apache-2.0ライセンス、リンクを設定しています。
初回の公開候補は0.1.0です。

```powershell
python -m pip install build "twine>=6.2"
python tools/build_python_packages.py
python -m twine check --strict dist/python/*
```

事前にPaperプラグインをビルドし、Debugパッケージ内のJARを更新します。
GitHub Actionsでは、この作業もビルドジョブで実行します。
SDKとRuntimeにはMinecraftのJARを含めません。
DebugにはPynnerプラグインのJARを含めますが、Paperサーバー本体は含めません。

ビルドスクリプトはソース配布（sdist）を作り、そのsdistからwheelを作ります。
成果物は`dist/python/`へ出力します。
初回は三つのwheelと三つのtar.gzが必要です。
公開対象に古い版が混ざっていないことを確認してください。

## Trusted Publisherを登録する

PyPIとTestPyPIには別々のアカウントと設定が必要です。
それぞれのPublishing設定から、三つの配布名に対してPending Publisherを登録します。
登録にはそのサービスへのログインが必要です。

| 項目 | 値 |
|---|---|
| PyPI project name | `minepynner`、`minepynner-runtime`、`minepynner-debug`（各一件） |
| Owner | `nehaverse-net` |
| Repository | `minepynner` |
| Workflow filename | `publish-python.yml` |
| Environment | PyPIは`pypi`、TestPyPIは`testpypi` |

GitHubのリポジトリ設定にも`pypi`と`testpypi`のEnvironmentを作ります。
公開対象のブランチをmainに制限し、必要ならレビュー担当者を設定します。
APIトークンをコードやGitHub Secretsへ登録する必要はありません。
短時間の認証情報を取得するTrusted Publishingを使います。
仕様は[PyPI公式の公開手順](https://docs.pypi.org/trusted-publishers/using-a-publisher/)を参照してください。

## TestPyPIで試す

GitHubのActionsで「Build and publish Python packages」を開き、mainでRun workflowします。
`publish`をtrue、`index`をtestpypiにして実行します。
pushやpull requestではビルドと検査だけが実行されます。
`publish=false`の手動実行でもアップロードしません。

TestPyPI公開後は、通常のPyPIからMessagePackを先に導入し、検証用の新しいPython環境で次を実行します。

```shell
python -m pip install "msgpack>=1.1,<2"
python -m pip install --no-deps --index-url https://test.pypi.org/simple/ minepynner==0.1.0 minepynner-runtime==0.1.0 minepynner-debug==0.1.0
python -m pip check
python -m pynner_runtime --help
python -m pynner_debug --help
```

TestPyPIには通常の依存パッケージが揃っているとは限らないため、導入元を分けています。
README表示、三つの依存関係、Debug内のJAR、実機の起動も確認します。

## PyPIへ公開する

同じWorkflowをmainで実行し、`publish=true`、`index=pypi`を選択します。
三つのプロジェクトに同じ版を公開します。
一部だけ成功した場合は、プロジェクトごとの公開状態を調べてから対応してください。
このWorkflowは既存のファイルを自動で読み飛ばす設定を使いません。

公開後、実際にpipから導入できることを確認してから、利用ガイドの「PyPI未公開」を更新します。
新しい版では三つのメタデータ、依存の固定版、Java JARの版と同梱パス、配布スクリプトのファイル名を揃えます。

詳しい配布形式は[Python Packagingの公式ガイド](https://packaging.python.org/en/latest/tutorials/packaging-projects/)を参照してください。

## 旧配布名から更新する

このプロジェクトの以前のローカルwheel（pynner、pynner-runtime、pynner-debug）を導入していた環境では、旧配布と新配布を同時にインストールしないでください。
同じimport先のファイルを使うため、片方のアンインストールで他方のファイルまで消える可能性があります。
デバッグやサーバーを停止してから、旧配布を外し、新しい三つのwheelをまとめて導入します。
別の作者のPyPIパッケージpynnerを使っている環境とは分けてください。

```shell
python -m pip uninstall pynner pynner-runtime pynner-debug
python -m pip install dist/minepynner-0.1.0-py3-none-any.whl dist/minepynner_runtime-0.1.0-py3-none-any.whl dist/minepynner_debug-0.1.0-py3-none-any.whl
python -m pip check
```
