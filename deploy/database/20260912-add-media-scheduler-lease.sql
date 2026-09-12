-- MS-07 一次性迁移。表为新增结构，不修改既有视频会话和运行态数据。
-- 新应用启动前执行；应用回滚时保留本表，不能删除其他实例仍在使用的租约。

CREATE TABLE IF NOT EXISTS media_scheduler_lease (
    lease_name VARCHAR(128) NOT NULL,
    lease_owner VARCHAR(64) NULL,
    locked_until DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (lease_name),
    INDEX idx_media_scheduler_lease_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 验收：同一 lease_name 只能存在一行；locked_until 到期后任一实例均可原子接管。
-- SELECT lease_name, COUNT(*) FROM media_scheduler_lease GROUP BY lease_name HAVING COUNT(*) > 1;
