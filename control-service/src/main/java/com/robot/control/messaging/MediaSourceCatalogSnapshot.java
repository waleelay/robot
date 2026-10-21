package com.robot.control.messaging;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.robot.control.fixedcamera.FixedCameraCatalogSnapshot;
import com.robot.media.common.video.VideoSourceType;
import java.time.Instant;
import java.util.List;

/**
 * Control 向统一媒体 Gateway 发布的授权源全量快照，时间使用 RFC3339。
 *
 * @param version MQTT 目录协议版本
 * @param gatewayId 目标 Gateway 标识
 * @param catalogVersion 本实例目录单调递增版本
 * @param issuedAt 目录签发时间
 * @param sources 尚未过期的授权媒体源集合，空集合撤销全部租约源
 */
public record MediaSourceCatalogSnapshot(
        String version,
        String gatewayId,
        long catalogVersion,
        @JsonSerialize(using = ToStringSerializer.class) Instant issuedAt,
        List<SourceRecord> sources) {

    /**
     * 将固定摄像头业务租约转换为通用 MQTT 源目录，不改变 BFF 内部 HTTP 契约。
     *
     * @param snapshot 已由 Control 校验并合并的固定摄像头授权目录
     * @return 可直接发布给统一 Gateway 的通用目录
     */
    public static MediaSourceCatalogSnapshot fromFixedCameras(FixedCameraCatalogSnapshot snapshot) {
        List<SourceRecord> records = snapshot.cameras().stream().map(camera -> new SourceRecord(
                VideoSourceType.FIXED_CAMERA, camera.cameraId(), camera.cameraId(), camera.enabled(),
                camera.protocolType(), camera.mainStreamUrl(), camera.subStreamUrl(), camera.expiresAt())).toList();
        return new MediaSourceCatalogSnapshot(snapshot.version(), snapshot.gatewayId(), snapshot.catalogVersion(),
                snapshot.issuedAt(), records);
    }

    /**
     * 按业务源和摄像头隔离的授权媒体输入配置。
     *
     * @param sourceType 业务设备类型，不作为输入协议
     * @param sourceId 平台权威视频源标识
     * @param deviceId 源内摄像头标识，固定摄像头等于 sourceId
     * @param enabled 是否允许本租约源被使用
     * @param sourceProtocol 已确认的输入协议，首批为 RTSP
     * @param mainStreamUrl 主码流地址，仅可信下游使用，可能含凭据
     * @param subStreamUrl 子码流地址，可空，仅可信下游使用
     * @param expiresAt 授权租约截止时间，过期后不得启动或探测
     */
    public record SourceRecord(
            VideoSourceType sourceType,
            String sourceId,
            String deviceId,
            boolean enabled,
            String sourceProtocol,
            String mainStreamUrl,
            String subStreamUrl,
            @JsonSerialize(using = ToStringSerializer.class) Instant expiresAt) {
    }
}
