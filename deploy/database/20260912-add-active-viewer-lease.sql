-- MS-06 一次性迁移。先停止旧 Media 实例并备份 media_session_viewer；新应用启动前执行。
-- 前置检查：活跃记录的 participant_identity 必须非空；异常数据应先人工核对。
-- SELECT id, session_id FROM media_session_viewer
--  WHERE left_at IS NULL AND (participant_identity IS NULL OR participant_identity = '');

ALTER TABLE media_session_viewer
    ADD COLUMN active_lease_key VARCHAR(128) NULL,
    ALGORITHM=INPLACE,
    LOCK=NONE;

-- 同一会话、同一浏览器的历史重复活跃记录仅保留心跳最新的一条。
-- 保留原记录，便于审计；执行时旧 Media 必须已停止，避免回填期间再插入。
CREATE TEMPORARY TABLE ms06_duplicate_viewers AS
SELECT id FROM (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY session_id, participant_identity
        ORDER BY last_heartbeat_at DESC, joined_at DESC, id DESC
    ) AS row_num
    FROM media_session_viewer
    WHERE left_at IS NULL AND participant_identity IS NOT NULL
) ranked WHERE row_num > 1;

UPDATE media_session_viewer viewer
JOIN ms06_duplicate_viewers duplicate ON duplicate.id = viewer.id
SET viewer.left_at = UTC_TIMESTAMP(6);

DROP TEMPORARY TABLE ms06_duplicate_viewers;

UPDATE media_session_viewer
SET active_lease_key = participant_identity
WHERE left_at IS NULL;

ALTER TABLE media_session_viewer
    ADD UNIQUE INDEX uk_session_viewer_active_lease (session_id, active_lease_key),
    ALGORITHM=INPLACE,
    LOCK=NONE;

-- 验收：以下两个查询均应返回 0。保留此列和唯一索引作为应用回滚兼容结构。
-- SELECT COUNT(*) FROM media_session_viewer
--  WHERE left_at IS NULL AND (active_lease_key IS NULL OR active_lease_key <> participant_identity);
-- SELECT COUNT(*) FROM media_session_viewer
--  WHERE left_at IS NOT NULL AND active_lease_key IS NOT NULL;
