# Protocol version 1

Javaが127.0.0.1のOS選択ポートを開き、Pythonがcontrolとeventsの2接続を開く。
トークンは起動ごとに生成し、環境変数PYNNER_TOKENで子プロセスへ渡す。
各接続のhelloにはトークン、channel、generationを含める。
認証後にwelcomeを返す。
TCP切断後、同じセッションへ自動再接続しない。

## フレーム

```text
uint32 big-endian payload length
MessagePack map
```

上限は1 MiB。
mapのキーは重複のないUTF-8 stringで、整数はsigned 64 bit、浮動小数点は有限値を使用する。
mapは最大4096要素、arrayは最大16384要素、ネストは最大32。
null、boolean、string、binary、array、mapを扱う。
pickleやJavaオブジェクトのデシリアライズを使わない。

```text
{
  "version": 1,
  "type": "operation",
  "generation": 7,
  "payload": {
    "request_id": 42,
    "owner": "weapons/fire_sword.py",
    "operation": "entity.set_fire",
    "target": {"uuid": "..."},
    "arguments": {"seconds": 5},
    "timeout_ms": 5000
  }
}
```

versionが異なる場合は拒否する。
generationはRuntimeの世代で、別世代の操作は実行直前にも拒否する。
request_idは各Runtime内の連番で、resultとの照合に使う。

## メッセージ

| type | 方向 | payload |
|---|---|---|
| hello | Python→Java | token、channel |
| welcome | Java→Python | capabilities |
| register | Python→Java | handlers、weapons、mobs |
| activate | Java→Python | 空map |
| ready | Python→Java | 空map |
| events | Java→Python | invocationsのarray |
| operation | Python→Java | request_id、owner、operation、target、arguments、timeout_ms |
| result | Java→Python | request_id、value、またはerrorとmessage |
| task_done | Python→Java | id |
| heartbeat | Python→Java | completed、queue、dropped |
| log | Python→Java | message |
| load_error | Python→Java | message |
| shutdown | Java→Python | 空map |

## 登録データ

handlerはid、owner、kindを持つ。
kind=eventはevent名とフィルターを持つ。
kind=commandはname、aliases、permission、player_only、arguments、completionsを持つ。
kind=taskはsecondsとrepeatingを持つ。
kind=weapon/mobはdefinitionとhookを持つ。
武器とMobの定義はIDをキーにしたmapで、hooks内にhook名→handler IDを持つ。

## イベントデータ

invocationはhandler IDと、その種類に応じたevent、context/argumentsを持つ。
イベントはevent名、cancelled、worldと、必要なplayer/entity/target/attackerのスナップショットを持つ。
Entityはuuid、type、name、location、velocity、pdc、fire_ticksを持つ。
LivingEntityにはhealthとmax_health、Playerにはfood、level、inventoryを加える。
UUIDだけがMinecraft操作の参照で、スナップショットは生きたJavaオブジェクトではない。

## 操作

```text
entity.snapshot / set_name / kill / set_health / damage / heal
entity.teleport / set_velocity / add_effect / remove_effect
entity.set_fire / lightning / spawn_particle / play_sound / set_pdc
player.send_message / send_actionbar / send_title
player.set_food / set_level / give_item / remove_item
world.spawn_mob / set_block / broadcast
server.broadcast / online_players / status
command.reply
scheduler.cancel
```

未知のoperationを拒否する。
対象、範囲、enum、PDC型などをJavaで検証する。
開始前の期限切れはTIMEOUTで拒否する。
既に実行された操作の巻き戻しは行わない。
Python側は5秒で応答待ちを終了し、同じ要求を再送しない。

## キュー

Javaの操作キューは既定4096件。
1 tick当たり200件、受付処理時間の目安2msでdrainする。
この予算は個々のPaper操作の実行時間を上限2msに制限するものではない。
Javaのイベントは通常と観測の2lane、それぞれ既定4096件で、合計8 MiBの上限を共有する。
1バッチは既定128件かつ900,000 bytes以下。
各invocationは512,000 bytes以下。
Pythonの受付は1024件かつ8 MiB、control操作は1024件かつ各要求64 KiB以下。
完了未確認の操作も1024件に制限する。
移動とMob tick通知は低優先度で、混雑時に間引く。
RPC要求間、異なるEntity、2接続間の全体順序は保証しない。

## エラー

ENTITY_GONE、INVALID_ARGUMENT、QUEUE_FULL、STALE_GENERATION、TIMEOUT、EXECUTION_ERRORを返す。
登録エラーは候補の有効化を拒否する。
Pythonのスクリプト例外はownerとhandlerを付けてlogへ送る。
連続5回失敗したhandlerはそのRuntime世代で停止する。
