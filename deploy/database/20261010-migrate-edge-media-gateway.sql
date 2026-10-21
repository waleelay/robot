-- 统一媒体 Gateway 发布模式迁移。执行前停止旧 Gateway、Control 与 Media，备份目标库。
-- 本脚本只作用于 Media 的已知 runtime 表，不修改组织授权、摄像头档案、Ingress 或发布版本。
-- 幂等更新；禁止在旧 Media 仍运行时执行，旧枚举解析器不能读取新值。

START TRANSACTION;

UPDATE media_source_runtime
   SET publisher_mode = 'EDGE_MEDIA_GATEWAY'
 WHERE source_type = 'FIXED_CAMERA'
   AND publisher_mode = 'FIXED_CAMERA_GATEWAY';

COMMIT;

-- 上线前应确认结果为 0；回滚必须在停止新服务后从备份或明确逆向脚本恢复。
SELECT COUNT(*) AS legacy_gateway_runtime_count
  FROM media_source_runtime
 WHERE publisher_mode = 'FIXED_CAMERA_GATEWAY';
