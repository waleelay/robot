package com.robot.control.fixedcamera;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/**
 * Control 合并所有有效租约后下发给指定 Gateway 的全量目录快照。
 *
 *  <p>该快照经 MQTT 下发给 Go 固定摄像头网关，属于机器间协议载荷，时间字段必须按
 *  RFC3339/ISO-8601 序列化（Go time.Time 的标准 JSON 格式）。全局 {@code DateTimeConfig}
 *  会把 Instant 统一格式化为 {@code yyyy-MM-dd HH:mm:ss}（前端展示格式），因此这里显式
 *  用 {@link ToStringSerializer} 覆盖，保证网关可以解析。
 *
 * @param version 协议或租约版本
 * @param gatewayId 目标固定摄像头 Gateway ID
 * @param catalogVersion 全量目录版本
 * @param issuedAt 签发时间
 * @param cameras 本租约或快照中的摄像头集合
 */
@Schema(name = "FixedCameraCatalogSnapshot", description = "Control 合并所有有效租约后下发给指定 Gateway 的全量目录快照。")
public record FixedCameraCatalogSnapshot(
        @Schema(
                description = "协议或租约版本",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String version,
        @Schema(
                description = "目标固定摄像头 Gateway ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String gatewayId,
        @Schema(description = "全量目录版本", requiredMode = Schema.RequiredMode.REQUIRED) long catalogVersion,
        @Schema(
                description = "签发时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                format = "date-time",
                types = {"string", "null"})
        @JsonSerialize(using = ToStringSerializer.class) Instant issuedAt,
        @Schema(
                description = "本租约或快照中的摄像头集合",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"array", "null"})
        List<CameraRecord> cameras) {

    /**
     * 可供固定摄像头编排使用的目录快照记录。
     *
     * @param cameraId 固定摄像头 ID
     * @param enabled 摄像头是否启用
     * @param protocolType 摄像头协议类型
     * @param mainStreamUrl 主码流地址，仅可信下游使用，可能包含凭据
     * @param subStreamUrl 子码流地址，仅可信下游使用，可能包含凭据
     * @param expiresAt 有效期截止时间
     */
    @Schema(name = "FixedCameraCatalogSnapshotCameraRecord", description = "可供固定摄像头编排使用的目录快照记录。")
    public record CameraRecord(
            @Schema(
                    description = "固定摄像头 ID",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    types = {"string", "null"})
            String cameraId,
            @Schema(description = "摄像头是否启用", requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
            @Schema(
                    description = "摄像头协议类型",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    types = {"string", "null"})
            String protocolType,
            @Schema(
                    description = "主码流地址，仅可信下游使用，可能包含凭据",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    types = {"string", "null"})
            String mainStreamUrl,
            @Schema(
                    description = "子码流地址，仅可信下游使用，可能包含凭据",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    types = {"string", "null"})
            String subStreamUrl,
            @Schema(
                    description = "有效期截止时间",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    implementation = String.class,
                    format = "date-time",
                    types = {"string", "null"})
            @JsonSerialize(using = ToStringSerializer.class) Instant expiresAt) {
    }
}
