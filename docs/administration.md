# 管理コマンドと権限

Pynner本体の管理コマンドは`/pynner`です。
ゲーム内では`/`を付け、Paperのconsoleでは省きます。
Pythonで登録する独自コマンドの詳細は[コマンドと定期実行](commands-and-tasks.md)にあります。

## コマンド一覧

| ゲーム内の入力 | 用途 | 実行元 | 必要な権限 |
|---|---|---|---|
| `/pynner reload` | 全Pythonスクリプトを候補として読み直す | Player、console | `pynner.admin.reload` |
| `/pynner status` | 有効世代、定義数、キューの状態を見る | Player、console | `pynner.admin.status` |
| `/pynner give <weapon_id>` | 指定した定義の武器を自分へ渡す | Player | `pynner.admin.give` |
| `/pynner spawn <mob_id>` | 自分の位置に指定した定義のMobを生成する | Player | `pynner.admin.spawn` |

コマンド自体の親権限は`pynner.admin`で、既定はOPです。
親権限には上の四つの子権限が含まれます。
権限プラグインで子権限を個別付与する場合も、コマンドへの入口の親権限チェックを考慮します。

## reloadの成功を判定する

`/pynner reload`は要求を受け付け、別プロセスで新しい候補を検証します。
受付メッセージだけでは完了していません。

成功時はPaperログに`Activated Python generation ...`が出ます。
statusで`active=true`と世代を確認します。
構文エラーなどで失敗した候補は有効化されず、旧世代が動き続けます。
詳しい切替順序は[手動反映](operations.md#手動で反映する)を参照してください。

`config.yml`の変更はreloadでは反映されません。
設定変更はPaper再起動が必要です。

## statusの読み方

| 項目 | 意味 | 読み違えやすい点 |
|---|---|---|
| `active` | 有効なPython Runtimeがあるか | 新しい変更が成功したかは世代とログも確認 |
| `generation` | Runtimeの識別番号 | 失敗候補の番号と有効世代を区別 |
| `weapons` | 武器定義の数 | 所持アイテムの個数ではない |
| `mobs` | Mob定義の数 | 出現中の個体数ではない |
| `operation_queue` | Java側で待っている作業数 | 長時間の増加は処理の停滞を調査 |
| `event_queue` | Java側で待っている通知数 | 一時的な増加だけで異常とは判断しない |
| `dropped_events` | Java側の欠落・間引き累計 | Python側の全破棄数は含まない |

## サンプルを試す

`examples/debug_demo.py`を読み込んだ場合です。

```text
/pynner give debug_blade
/pynner spawn debug_target
```

IDはPythonの`@weapon("debug_blade")`や`@mob("debug_target")`に書いた値です。
表示名やMaterial名をIDの代わりに入力しても、その定義を選択できません。
登録されていないIDの場合は、ファイルの配置、reloadの成功、定義IDを確認します。

## Debug Modのコマンド

Fabric側の`/pynnerclient`はDebug画面を開きます。
F8も同じ画面を開く既定キーです。
このクライアントコマンドはPaperの管理コマンドと別で、Debug Modを入れたクライアントだけにあります。
画面のReloadボタンも、接続中の試験サーバーのPython反映を要求します。
