-- MS-02 一次性生产迁移；执行前备份 robot_media.media_video_session。
-- 本脚本不做历史数据批量回填，旧会话在首次复用时原位关联 runtime_id。

CREATE TABLE IF NOT EXISTS media_source_runtime (
    runtime_id VARCHAR(64) NOT NULL,
    source_type ENUM('FIXED_CAMERA', 'ROBOT_CAMERA') NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    channel ENUM('fusion', 'thermal', 'visible') NOT NULL,
    quality ENUM('auto', 'main', 'sub') NOT NULL,
    room_name VARCHAR(160) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (runtime_id),
    UNIQUE KEY uk_source_runtime_source (source_type, source_id, device_id, channel, quality)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE media_video_session
    ADD COLUMN runtime_id VARCHAR(64) NULL,
    ADD INDEX idx_video_session_runtime_status (runtime_id, status),
    ALGORITHM=INPLACE,
    LOCK=NONE;
