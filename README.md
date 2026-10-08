# Pynner 0.1.0

通常の `.py` ファイルでPaperサーバーへ機能を追加するフレームワークです。
JavaがMinecraft操作と即時ルールを担当し、別プロセスのCPythonがスクリプトを実行します。
作者はgrampr、Javaパッケージは `net.nehaverse.pynner` です。

初めて使う場合は、[日本語の利用ガイド](docs/index.md)から読み進めてください。
導入、要素の関係、武器とMobの全設定、イベントの引数、操作API、反映方法、エラーの対処を説明しています。

Fabric 1.21.11からローカルで試す場合は、[デバッグModの導入と実行](docs/fabric-debug.md)を参照してください。
起動済みのMinecraftへPythonから自動接続し、試験専用のPaperワールドで実行できます。

## 対応環境

- Paper 1.21.11、Java 21以上。成果物のclassfileはJava 21向けです。
- CPython 3.11以上。追加ライブラリはサーバー用Python環境へpipで導入できます。
- WindowsとLinux向けのTCP通信。実機確認はWindows／Paper 1.21.11で実施済みです。
- Foliaは実行対象別Schedulerへの分離まで。対応認証済みとは扱わず、`folia-supported` は指定していません。
- 本番サーバーへの接続にはクライアントModは不要です。開発用のFabric Debug Modを別JARで同梱します。

現行Paper 26.1以降はJava 25が必要です。
この版はJava 21を優先してPaper 1.21.11へ固定しています。
公式資料: https://docs.papermc.io/paper/getting-started/

## 導入

1. `dist/pynner-paper-0.1.0.jar` をサーバーの `plugins/` へコピーします。
2. 初回起動で `plugins/Pynner/` 以下のフォルダーと設定を生成します。
3. 普段使うPythonへ同梱SDK・Runtime wheelをインストールします。仮想環境は任意です。
4. `config.yml`の`python.executable`へ、そのPythonの絶対パスを設定し、Paperを再起動します。

Windowsの例:

```powershell
python -m pip install D:/pynner/dist/minepynner-0.1.0-py3-none-any.whl D:/pynner/dist/minepynner_runtime-0.1.0-py3-none-any.whl
python -c "import sys; print(sys.executable)"
```

最後の出力を設定へ使います。例えば:

```yaml
python:
  executable: 'C:/Python314/python.exe'
runtime:
  auto-reload: false
```

[導入を順に進める](docs/getting-started.md) / [Python環境と任意のvenv](docs/python-environment.md) / [Fabricで最初に動かす](docs/debug-quickstart.md)。
`msgpack`はRuntimeの依存としてpipが導入します。
現在はPyPI未公開のため、同梱wheelを使ってください。

## 検索付きドキュメントサイト

導入・運用、Fabricデバッグ、スクリプト開発、設定リファレンスを章別に読めます。

```powershell
python -m pip install -r requirements-docs.txt
python -m mkdocs serve --dev-addr 127.0.0.1:8008
```

`http://127.0.0.1:8008/`を開きます。
配布HTMLの閲覧・ビルド方法は[ドキュメントの使い方](docs/documentation.md)を参照してください。

## 最短のHello World

将来のPyPI公開後は、開発用環境へ次のように導入できます。

```bash
pip install minepynner
```

**公開予定の配布名は`minepynner`です。`pynner`は別の作者のPyPIプロジェクトです。この成果物はPyPIへ未公開です。現在は同梱wheel、または `pip install -e ./pynner-sdk` を使ってください。**
IDE用Pythonとサーバー用Pythonが異なる場合、それぞれにSDKを導入します。

```python
from pynner import PlayerJoinEvent, event

@event("player_join")
def join(e: PlayerJoinEvent) -> None:
    e.player.send_message("Hello!")
```

`plugins/Pynner/scripts/test.py` に保存して反映します。

```text
/pynner reload
```

初回に生成するディレクトリ:

```text
plugins/Pynner/
├─ scripts/
│  ├─ weapons/
│  ├─ mobs/
│  ├─ events/
│  └─ plugins/
├─ config.yml
├─ messages.yml
├─ logs/
└─ runtime/
```

`scripts/` 以下の `.py` を再帰的にロードします。
`.` または `_` で始まるファイルをエントリーポイントとして実行しません。
補助コードは `_helpers.py` などに置き、通常のimportで読み込めます。
同じ定義IDまたはコマンド名は、別ファイルで重複登録できません。

## 武器とMob

`examples/fire_sword.py` と `examples/boss_zombie.py` を `scripts/` 以下へコピーしてください。
reload後、OPで次を実行できます。

```text
/pynner give fire_sword
/pynner spawn boss_zombie
```

武器はID、表示名、Material、lore、CustomModelData、攻撃属性、速度、耐久値、enchant、AttributeModifier、cooldown、使用回数、PDC、shaped recipeに対応します。
`damage` は基礎攻撃属性で、最終ダメージの固定値ではありません。
`cooldown` と `uses` は成功した近接攻撃へ適用します。
属性以外のPython効果はIPCとSchedulerによる遅延を伴います。

武器フックは `on_left_click`、`on_right_click`、`on_hit`、`on_kill`、`on_damage`、`on_break`、`on_equip`、`on_unequip` です。
`on_damage` は使用者が受けたダメージ、equipはプレイヤーのメインハンド定義IDの変化です。
弓の発射時に武器IDをProjectileへ結び付ける機能は、この版には含めません。

Mobはbase、名前、HP、攻撃、armor、速度、AI、equipment、drops、experience、size、attribute、PDCに対応します。
Entity種別が持たない属性は登録時に拒否します。
カスタムMobと武器は、既存のEntityTypeとMaterialへ機能を追加するものです。
独自の外見には別途リソースパックが必要です。

Mobフックは `on_spawn`、`on_tick`、`on_attack`、`on_damage`、`on_death`、`on_target`、`on_move`、`on_interact` です。
`on_spawn` と `on_tick` はEntityを受け取り、ほかはEventを受け取ります。
`on_tick` の間隔は `tick_seconds`（既定1秒）で指定します。
reload後も既存Mobの属性と装備は維持し、新しい定義の属性は新規spawnに適用します。
PDCを使うため、チャンク再ロードとサーバー再起動後もIDを識別できます。

## SDKの操作契約

```python
player.health            # イベント取得時またはrefresh時の値
player.health = 20        # 変更要求を送る
player.set_fire(5)        # 変更要求を送る
await player.set_health(20)  # Java側での実行完了を確認する
await player.refresh()      # 最新スナップショットを取得する
```

プロパティの読み取りで隠れた同期RPCは行いません。
代入後も読み取り値は、次のrefreshまで以前のスナップショットです。
操作メソッドはawait可能な `OperationReceipt` を返します。
awaitしなかった操作の失敗もログへ出ます。
操作の順序と実行完了が必要な場合は、各操作を順にawaitしてください。
タイムアウトは「実行されなかった」という保証ではなく、応答が得られなかったことを示す場合があります。
副作用のある操作を自動再送しません。

Playerにはmessage、actionbar、title、food、level、inventory、アイテム授受があります。
Entityにはteleport、kill、damage、heal、velocity、effect、fire、particle、sound、PDCがあります。
spawnしたMobは `entity = await spawn_mob(id, location)` でEntityとして受け取れます。
`give_item` の戻り値はinventoryに入らなかったアイテムのスナップショット一覧です。
アンロードされたEntity、死亡したEntityへの操作は `ENTITY_GONE` で失敗します。

`Material` と `EntityType` は対象Paper APIから生成した列挙型で、文字列指定も可能です。
`py.typed`、dataclass、Protocol、TypedDict、型注釈を同梱します。

## イベントとキャンセル

対応イベント:

```text
player_join / player_quit / player_move / player_interact
entity_damage / entity_damage_by_entity / entity_death / entity_spawn
block_break / block_place / inventory_click / projectile_hit / async_chat
```

`@event(PlayerJoinEvent)` と `@event("player_join")` は同じ購読です。
フィルターとしてworld、entity_type、material、permission、min_distance、rate_limitを指定できます。
`rate_limit` はEntityごとの毎秒通知上限です。
通常はキャンセル済みイベントを通知しません。必要な場合は `include_cancelled=True` を指定します。

Pythonが受け取るEventは読み取り用スナップショットです。
元のPaperイベントを、後からPythonでキャンセルすることはできません。
即時キャンセルにはJava側で評価する宣言を使います。

```python
@event("block_break", material="DIAMOND_BLOCK", cancel=True)
def protect(e):
    e.player.send_message("Protected!")
```

この例はJavaのイベントハンドラー内で条件を評価し、キャンセルします。
ほかのプラグインとの優先順位により、後続ハンドラーが結果を変える場合があります。
AsyncChatのworld、permissionなどはメインスレッドで更新したキャッシュを使い、最大約10 ticksの遅れがあります。
AsyncChatハンドラーからPaperの状態を直接読まず、Pythonのasync関数からもJava操作キューを使います。

## コマンドとScheduler

`examples/commands.py` に、player-only、console、aliases、permission、Playerとintの引数の例があります。
対応引数は `str`、`int`、`float`、`bool`、オンラインの `Player` です。
Playerとboolは型から補完し、ほかの候補は `completions={"arg": ["a", "b"]}` で登録できます。
コマンドの引数は空白区切りで、可変長や引用符による複数単語引数は未対応です。
consoleでは `ctx.player is None` です。返信には `ctx.reply()` を使います。

```python
@scheduler.every(seconds=1)
def tick():
    ...

@scheduler.delay(seconds=5)
def delayed():
    ...

# 有効化後にキャンセルする場合:
# tick.task.cancel()
```

secondsは20 ticks／秒に換算するゲーム時間です。
定期タスクが未処理の場合、同じタスクを積み上げません。
Minecraftの操作場所はEntity／Region／Global／AsyncのScheduler境界で分離しています。

## ライフサイクルとreload

各エントリーファイルに `on_load()`、`on_enable()`、`on_disable()` を定義できます。
同期関数とasync関数の両方に対応します。
`on_load` は登録と準備の段階で、SDK経由のMinecraft操作は許可しません。
`on_enable` からMinecraft操作を使えます。
`on_disable` はローカル資源の解放用です。reloadで旧世代のMinecraft操作は拒否されます。

reloadは、新しいCPythonプロセスへ全エントリーファイルを読み込む方式です。
ロード、on_load、登録検証が失敗した場合は旧Runtimeを維持します。
登録を切り替え、旧世代のハンドラーとタスクを解除します。
旧世代から遅れて届いた操作を拒否します。
任意Pythonコードのファイル書き込み、外部サービス操作、on_enableの副作用までは巻き戻せません。
Python上のメモリ状態とモジュールは再作成されます。

`runtime.auto-reload: true` で、`.py` の内容変更、追加、削除を検知します。
約750msのdebounceを使い、保存途中のロードを避けます。
本番の既定値はOFFです。
Python実行パスと自動reload設定自体の変更にはPaperの再起動が必要です。

## 過負荷と障害

TCPはlocalhost限定で、起動ごとのトークンを使って両接続を認証します。
イベント未購読時にはIPCへイベントを送りません。
イベントはバッチ化し、件数とバイト数に上限を設けます。
移動とMob tick通知は低優先度にし、Pythonが詰まると間引きます。
元の移動経路を保持する保証はありません。
重要イベントも上限を超えると欠落し得るため、`/pynner status` のdrop数を監視してください。

Python例外は `logs/python-<generation>.log` とMinecraftコンソールへ出します。
プロセスのstdout／stderrは `logs/runtime-<generation>.log` に保存します。
ハンドラーが連続5回失敗すると、その世代では実行を停止します。
Pythonクラッシュ、heartbeat停止、処理進捗停止は監視して復旧します。
自動再起動には回数制限があり、管理者のreloadで再試行できます。
スクリプトは管理者が配置する信頼済みコードとして扱います。
同じOSユーザー権限を持つ任意コードに対するSandboxやメモリ隔離の保証はありません。

## 管理コマンド

| コマンド | 権限 |
|---|---|
| `/pynner reload` | `pynner.admin.reload` |
| `/pynner status` | `pynner.admin.status` |
| `/pynner give <weapon_id>` | `pynner.admin.give` |
| `/pynner spawn <mob_id>` | `pynner.admin.spawn` |

親権限は `pynner.admin`、既定はOPです。
Pythonコマンド自身のpermissionは、各decoratorで指定します。

## ビルドと検証

```powershell
mvn -B package
python -m venv .venv
.venv/Scripts/python.exe -m pip install -e ./pynner-sdk -e ./pynner-runtime -e ./pynner-debug pytest build mypy ruff
.venv/Scripts/python.exe -m pytest -q
.venv/Scripts/python.exe -m mypy pynner-sdk/src pynner-runtime/src pynner-debug/src examples
.venv/Scripts/python.exe -m ruff check pynner-sdk/src pynner-runtime/src pynner-debug/src examples tests
.venv/Scripts/python.exe -m build --wheel pynner-sdk --outdir dist
.venv/Scripts/python.exe -m build --wheel pynner-runtime --outdir dist
.venv/Scripts/python.exe -m build --wheel pynner-debug --outdir dist
Push-Location pynner-fabric
.\gradlew.bat --console=plain build
Pop-Location
```

設計と実装段階は [docs/architecture.md](docs/architecture.md)、通信契約は [docs/protocol.md](docs/protocol.md)、実機での確認内容は [docs/verification.md](docs/verification.md) に記載します。
`tools/` のsmokeスクリプトは検証用で、本番のscriptsへコピーしないでください。

## PyPI公開の準備

三つの配布名は`minepynner`、`minepynner-runtime`、`minepynner-debug`です。
import名の`pynner`、`pynner_runtime`、`pynner_debug`は維持します。
[公開手順](docs/publishing.md)にビルド、TestPyPI、Trusted Publisherの登録項目を記載しています。
GitHub Actionsの`publish-python.yml`は通常のpushでは検査だけを行い、公開は手動実行で指定します。
