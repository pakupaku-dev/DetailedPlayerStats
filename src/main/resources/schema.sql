CREATE TABLE IF NOT EXISTS detailed_stats_servers (
 server_id VARCHAR(96) PRIMARY KEY, installed_ms BIGINT NOT NULL,
 heartbeat_ms BIGINT NOT NULL, version VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS reward_quest_queue (seq BIGINT AUTO_INCREMENT PRIMARY KEY,event_id CHAR(36) CHARACTER SET ascii NOT NULL UNIQUE) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reward_login_queue (seq BIGINT AUTO_INCREMENT PRIMARY KEY,event_id CHAR(36) CHARACTER SET ascii NOT NULL UNIQUE) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS detailed_stats_batches (
 batch_id CHAR(36) CHARACTER SET ascii PRIMARY KEY, committed_ms BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_snapshots (
 player_uuid CHAR(36) CHARACTER SET ascii NOT NULL, server_id VARCHAR(96) NOT NULL,
 player_name VARCHAR(64) NOT NULL, statistics JSON NOT NULL, observed_ms BIGINT NOT NULL,
 source VARCHAR(32) NOT NULL, PRIMARY KEY(player_uuid,server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_legacy_placements (
 player_uuid CHAR(36) CHARACTER SET ascii NOT NULL, server_id VARCHAR(96) NOT NULL,
 counts JSON NOT NULL, observed_ms BIGINT NOT NULL, PRIMARY KEY(player_uuid,server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_events (
 event_id CHAR(36) CHARACTER SET ascii PRIMARY KEY, player_uuid CHAR(36) CHARACTER SET ascii NOT NULL,
 player_name VARCHAR(64) NOT NULL, server_id VARCHAR(96) NOT NULL, occurred_ms BIGINT NOT NULL,
 kind VARCHAR(48) NOT NULL, subject VARCHAR(128) NOT NULL, amount DECIMAL(24,6) NOT NULL,
 world_uuid CHAR(36) CHARACTER SET ascii NOT NULL, world_name VARCHAR(128) NOT NULL,
 x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, biome VARCHAR(128) NOT NULL,
 game_mode VARCHAR(24) NOT NULL, tool JSON NOT NULL, context JSON NOT NULL,
 INDEX player_time(player_uuid,occurred_ms), INDEX server_time(server_id,occurred_ms),
 INDEX player_kind_time(player_uuid,kind,occurred_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_daily (
 player_uuid CHAR(36) CHARACTER SET ascii NOT NULL, server_id VARCHAR(96) NOT NULL,
 day DATE NOT NULL, world_uuid CHAR(36) CHARACTER SET ascii NOT NULL, world_name VARCHAR(128) NOT NULL,
 kind VARCHAR(48) NOT NULL, subject VARCHAR(128) NOT NULL,
 amount DECIMAL(24,6) NOT NULL, event_count BIGINT NOT NULL,
 PRIMARY KEY(player_uuid,server_id,day,world_uuid,kind,subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_imports (
 server_id VARCHAR(96) PRIMARY KEY, last_row BIGINT NOT NULL, high_row BIGINT NOT NULL,
 finished BOOLEAN NOT NULL DEFAULT FALSE, updated_ms BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS detailed_stats_coreprotect_blocks (
 server_id VARCHAR(96) NOT NULL, source_row BIGINT NOT NULL, occurred_ms BIGINT NOT NULL,
 player_uuid VARCHAR(64) NOT NULL, actor_name VARCHAR(100) NOT NULL, world_name VARCHAR(128) NOT NULL,
 x INT NOT NULL,y INT NOT NULL,z INT NOT NULL, material VARCHAR(128) NOT NULL,
 legacy_data INT NOT NULL, action INT NOT NULL, rolled_back INT NOT NULL,
 PRIMARY KEY(server_id,source_row),INDEX player_time(player_uuid,occurred_ms),
 INDEX player_action_time(player_uuid,action,occurred_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
