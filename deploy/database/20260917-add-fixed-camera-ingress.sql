ALTER TABLE media_source_runtime
    ADD COLUMN publisher_mode VARCHAR(32) NULL,
    ADD COLUMN publisher_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ingress_operation_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN accepted_ingress_operation VARCHAR(16) NULL,
    ADD COLUMN ingress_id VARCHAR(128) NULL,
    ADD COLUMN last_stream_status VARCHAR(16) NULL,
    ADD COLUMN last_reason_code VARCHAR(64) NULL,
    ADD COLUMN last_verified_at DATETIME(6) NULL,
    ADD UNIQUE INDEX uk_source_runtime_ingress_id (ingress_id),
    ALGORITHM=INPLACE,
    LOCK=NONE;

UPDATE media_source_runtime
   SET publisher_mode = CASE
       WHEN source_type = 'FIXED_CAMERA' THEN 'FIXED_CAMERA_GATEWAY'
       ELSE 'DEVICE_CLIENT'
   END
 WHERE publisher_mode IS NULL;

ALTER TABLE media_source_runtime
    MODIFY COLUMN publisher_mode VARCHAR(32) NOT NULL,
    ALGORITHM=INPLACE,
    LOCK=NONE;
