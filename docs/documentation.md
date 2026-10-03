# ドキュメントの閲覧・ビルド

このドキュメントはMarkdownと、検索付きのHTMLサイトの二つの形で読めます。
Pynnerを動かすために、ドキュメント用のライブラリを入れる必要はありません。

## 配布HTMLを読む

`dist/pynner-0.1.0-docs-site.zip`を展開し、`site/index.html`をブラウザーで開きます。
左側の章一覧、右側のページ内目次、日本語検索、コードのコピーボタン、明暗切替を使えます。
サイト本体・検索データ・スタイルは配布ZIPに含まれます。
外部サイトのリンクはインターネット接続が必要です。
Mermaid図の描画はテーマが外部ライブラリを取得するため、ネット接続が必要な場合があります。
ブラウザーがローカルファイルを制限する場合は、下のHTTPプレビューを使います。

## ソースからサイトを作る

リポジトリのルートで実行します。
ドキュメント作成用にも仮想環境は必須ではありません。

```powershell
Set-Location D:/pynner
python -m pip install -r requirements-docs.txt
python -m mkdocs build --strict
```

出力は`site/`です。
`--strict`ではビルド警告も失敗として扱うので、設定や内部リンクを点検できます。
テーマは[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/)を使っています。

## HTTPプレビュー

生成済みHTMLを配信する場合です。

```powershell
python -m http.server 8008 --bind 127.0.0.1 --directory site
```

ブラウザーで`http://127.0.0.1:8008/`を開きます。
ターミナルのCtrl+Cで終了します。
編集しながら自動更新する場合は、代わりに次を使います。

```powershell
python -m mkdocs serve --dev-addr 127.0.0.1:8008
```

## 本体の配布物を作る

ソースだけをcloneした場合、`dist/`のwheelやJARは生成されていません。
既存のビルドスクリプトは開発用`.venv`のPythonを使います。
これはビルド手順の都合で、出来上がったPynnerの実行にvenvを必須とするものではありません。

Java 21以上、MavenとPythonを用意し、リポジトリのルートで実行します。

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install build
./tools/build-release.ps1
```

Fabricのビルドには同梱Gradle wrapperを使い、依存をダウンロードするネット接続が必要です。
`JAVA_HOME`は使うJDKを指定します。
JAR、三つのwheel、Debug Modと配布ZIPが`dist/`へ生成されます。

サイトも同梱したドキュメントZIPを作る場合は、先にサイトをビルドしてから再パッケージします。

```powershell
.venv/Scripts/python.exe -m pip install -r requirements-docs.txt
.venv/Scripts/python.exe -m mkdocs build --strict
.venv/Scripts/python.exe tools/package_release.py
```

## 編集する場所

| ファイル | 役割 |
|---|---|
| `docs/*.md` | ページ本文 |
| `mkdocs.yml` | 章構成、テーマ、検索設定 |
| `docs/assets/stylesheets/extra.css` | 文字・余白・表の調整 |
| `requirements-docs.txt` | ビルド環境で検証した版 |

ページを追加したら`mkdocs.yml`の`nav`にも登録します。
内部リンクはMarkdownの相対パスで書き、生成先のHTMLパスを直接書かないようにします。
