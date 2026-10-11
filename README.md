# DetailedPlayerStats 1.0.1

Paper 26.2 / Java 25向けの専用プレイヤー統計プラグインです。
配布ファイルは`target/DetailedPlayerStats-1.0.1.jar`です。

## 記録内容

- 採掘・設置: ブロック種類、ブロック状態、ワールドUUID・名前、XYZ座標、バイオーム、日時、ゲームモード、記録時の主手の道具・エンチャント・消耗値。
- BlockMultiPlaceEventの複数設置、実際のドロップ、収穫、バケツの採取・使用。
- Mob討伐・プレイヤー討伐・死亡・ダメージ量・原因・関連エンティティUUID。
- 消費、釣り、精錬品回収、手なずけ、繁殖、ログイン・退出・ワールド移動。
- Bukkitの統計増分（作成、取得、使用、破損など）。移動・時間の高頻度統計は30秒ごとの全統計スナップショットで保存。
- プレイヤー・サーバー・日付（UTC）・ワールド・行動・対象ごとの集計。

キャンセル済みの操作は記録しません。道具はイベント発生時の主手です。飛び道具を発射した瞬間の道具と同一とは限りません。チャット、入力コマンド、IP、認証情報は保存しません。WorldEdit等、通常のBukkitイベントも統計増分も発行しない直接変更は新規イベント記録の対象外です。

## 保存と重複防止

MySQLには`detailed_stats_`で始まるテーブルを作成します。インベントリは引き続きWorldStateSyncが管理し、本プラグインはゲーム内のアイテムや残高を変更しません。

イベントをまずローカル`outbox/*.open`へ追記し、通常2秒ごと／500レコードごとに同期書き出しして`.jsonl`へ確定します。別スレッドからMySQLの同一トランザクションでイベントと集計を保存し、成功後にローカルファイルを消します。バッチUUIDとイベントUUIDで再送を重複計上しません。MySQL障害時はファイルを保留し自動再送します。ローカルディスク障害はログに重大エラーを出します。OS電源断では最後の同期書き出し以降、最大通常2秒程度のデータが失われ得ます。プロセス停止で不完全になった最後の行は復旧時に除外します。

イベント・集計は自動削除しません。ディスク使用量を監視してください。DBエラーと保留件数は`/dstats status`で確認できます。

## 既存データ

起動時に`world/players/stats`（26.2）および`world/stats`のJSONを取り込みます。設定`statistics-directories`で別の保存先も指定できます。次元フォルダの親にある統計も探索します。同じUUIDの複数ファイルは新しいファイルを採用します。取り込みは絶対値と保存日時を使うため繰り返しても加算されず、新しいオンライン値を古いファイルで上書きしません。Minecraft側で統計をリセットすると絶対値も変わりますが、本プラグインのイベント履歴・導入後集計は残ります。

WorldStateSyncに残る設置数は初回導入以前の最新データを一度だけ保存し、以降の本プラグインの設置数と合算します。`installation-ms.txt`とMySQLの初回記録は削除しないでください。

CoreProtectのSQLiteがある場合は読み取り専用で過去のブロック操作を取り込みます。対象は初回導入以前に記録された実プレイヤーUUID付き履歴です。日時・座標・世界・ブロック種類・操作番号・ロールバック状態を保存します。自然現象などUUIDがない記録、チャット・コマンド履歴、バイナリメタデータは取り込みません。元のCoreProtect DBは変更しません。通常累計へは加算せず、独立した過去履歴として表示します。新規イベントと過去履歴の両方を足す集計は行いません。

取り込みは1万行ずつ処理し、MySQLのチェックポイントを同時に確定するため、再起動後も途中から続きます。`detailed_stats_imports.finished`で完了確認できます。存在しない過去の情報（当時の道具など）は推測しません。

## 導入

対象の全Paperバックエンドに配置します。Velocity本体には配置しません。サーバーIDは既定で`Paper:<port>`です。インポート後は変更しないでください。

既定では隣の`WorldStateSync/config.yml`のMySQL接続情報を読みます。単独で使う場合は`inherit-worldstatesync-mysql: false`にして本プラグインの`mysql`を設定します。接続ユーザーは保存先DBのCREATE/SELECT/INSERT/UPDATE権限が必要です。CoreProtectの取り込みを使わない場合は`import-coreprotect: false`にします。

コマンドは`/detailedstats status`、`/detailedstats flush`、`/detailedstats import`（短縮`/dstats`）。権限`detailedstats.admin`は既定OPです。importは通常統計ファイルを再確認します。CoreProtectは初回の取り込み範囲とチェックポイントを保持します。

## ビルド・検証

JDK 25とMaven 3.9以降を使用します。`mvn package`でJARを作ります。JournalTestはディスク再起動復旧とバッチ境界、統計の名前変換を検証します。StoreTestは`DPS_TEST_DB/HOST/PORT/USER/PASSWORD`で指定した隔離MySQLに対して、再送、ロールバック、古い統計の再インポート、CoreProtectチェックポイントを検証します。未設定時はMySQLテストだけをスキップします。

## ポータル

1.0.1ではDailyQuests/LoginBonus用のMySQLイベントキューを統計イベントと同時に保存します。再送してもキューを重複させません。オンラインのまま日本時間の日付が変わった場合も`reward_presence`を定期処理で記録します。詳細は[DailyQuests](https://github.com/pakupaku-dev/DailyQuests) / [LoginBonus](https://github.com/pakupaku-dev/LoginBonus)を参照。

`minecraft-user-portal`の本人認証APIが専用テーブルを読み、通常統計と「行動記録」を表示します。最新100件の詳細、直近30日の日別集計、種類別集計、CoreProtect過去履歴、サーバーの稼働・取り込み状況を確認できます。DBには表示範囲より古い記録も残ります。
