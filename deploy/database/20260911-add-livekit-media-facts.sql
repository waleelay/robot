-- MS-04 一次性生产迁移；执行前检查 room_name 无重复并备份 media_source_runtime。

ALTER TABLE media_source_runtime
    ADD COLUMN publisher_identity VARCHAR(128) NULL,
    ADD COLUMN publisher_participant_sid VARCHAR(128) NULL,
    ADD COLUMN track_sid VARCHAR(128) NULL,
    ADD COLUMN track_name VARCHAR(128) NULL,
    ADD COLUMN last_media_at DATETIME(6) NULL,
    ADD UNIQUE INDEX uk_source_runtime_room_name (room_name),
    ALGORITHM=INPLACE,
    LOCK=NONE;
