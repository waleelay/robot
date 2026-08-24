package com.robot.control.fixedcamera;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/**
 * Control 合并所有有效租约后下发给指定 Gateway 的全量目录快照。
 *
 * <p>该快照经 MQTT 下发给 Go 固定摄像头网关，属于机器间协议载荷，时间字段必须按
 * RFC3339/ISO-8601 序列化（Go time.Time 的标准 JSON 格式）。全局 {@code DateTimeConfig}
 * 会把 Instant 统一格式化为 {@code yyyy-MM-dd HH:mm:ss}（前端展示格式），因此这里显式
 * 用 {@link ToStringSerializer} 覆盖，保证网关可以解析。
 */
public record FixedCameraCatalogSnapshot(
        String version,
        String gatewayId,
        long catalogVersion,
        @JsonSerialize(using = ToStringSerializer.class) Instant issuedAt,
        List<CameraRecord> cameras) {

    public record CameraRecord(
            String cameraId,
            boolean enabled,
            String protocolType,
            String mainStreamUrl,
            String subStreamUrl,
            @JsonSerialize(using = ToStringSerializer.class) Instant expiresAt) {
    }
}
