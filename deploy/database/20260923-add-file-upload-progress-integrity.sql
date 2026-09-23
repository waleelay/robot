-- 执行前必须先处理重复的 robot_id + source_file_id；迁移失败时不得自动删除业务文件。
CREATE TABLE IF NOT EXISTS media_file_source_lock (
    lock_id VARCHAR(64) NOT NULL,
    robot_id VARCHAR(64) NOT NULL,
    source_file_id VARCHAR(256) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (lock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT robot_id, source_file_id, COUNT(*) AS duplicate_count
FROM media_file
WHERE robot_id IS NOT NULL AND source_file_id IS NOT NULL
GROUP BY robot_id, source_file_id
HAVING COUNT(*) > 1;

ALTER TABLE media_file
    DROP INDEX idx_file_robot_source,
    ADD UNIQUE KEY uk_file_robot_source (robot_id, source_file_id);

ALTER TABLE media_file_upload
    ADD COLUMN completion_owner VARCHAR(64) NULL AFTER completed_at,
    ADD COLUMN completion_lease_expires_at DATETIME(6) NULL AFTER completion_owner,
    ADD UNIQUE KEY uk_file_upload_storage (storage_upload_id);
