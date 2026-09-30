package com.robot.control.fixedcamera;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * BFF 使用当前用户授权列表生成的固定摄像头短租约。
 *
 * @param leaseId 目录租约 ID
 * @param version 协议或租约版本
 * @param issuedAt 签发时间
 * @param expiresAt 有效期截止时间
 * @param cameras 本租约或快照中的摄像头集合
 */
@Schema(name = "FixedCameraCatalogLeaseRequest", description = "BFF 使用当前用户授权列表生成的固定摄像头短租约。")
public record FixedCameraCatalogLeaseRequest(
        @Schema(description = "目录租约 ID", types = {"string", "null"}) String leaseId,
        @Schema(description = "协议或租约版本") long version,
        @Schema(
                description = "签发时间",
                implementation = String.class,
                example = "2026-09-29T12:00:00+08:00",
                types = {"string", "null"})
        Instant issuedAt,
        @Schema(
                description = "有效期截止时间",
                implementation = String.class,
                example = "2026-09-29T12:00:00+08:00",
                types = {"string", "null"})
        Instant expiresAt,
        @Schema(description = "本租约或快照中的摄像头集合", types = {"array", "null"}) List<CameraRecord> cameras) {

    /**
     * 用户目录租约中的摄像头记录，携带可信下游所需的连接信息。
     *
     * @param cameraId 固定摄像头 ID
     * @param enabled 摄像头是否启用
     * @param protocolType 摄像头协议类型
     * @param mainStreamUrl 主码流地址，仅可信下游使用，可能包含凭据
     * @param subStreamUrl 子码流地址，仅可信下游使用，可能包含凭据
     */
    @Schema(name = "FixedCameraCatalogLeaseRequestCameraRecord", description = "用户目录租约中的摄像头记录，携带可信下游所需的连接信息。")
    public record CameraRecord(
            @Schema(description = "固定摄像头 ID", types = {"string", "null"}) String cameraId,
            @Schema(description = "摄像头是否启用") boolean enabled,
            @Schema(description = "摄像头协议类型", types = {"string", "null"}) String protocolType,
            @Schema(description = "主码流地址，仅可信下游使用，可能包含凭据", types = {"string", "null"}) String mainStreamUrl,
            @Schema(description = "子码流地址，仅可信下游使用，可能包含凭据", types = {"string", "null"}) String subStreamUrl) {
    }
}
