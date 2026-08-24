package com.robot.control.fixedcamera;

import java.time.Instant;
import java.util.List;

/** Control 合并所有有效租约后下发给指定 Gateway 的全量目录快照。 */
public record FixedCameraCatalogSnapshot(
        String version,
        String gatewayId,
        long catalogVersion,
        Instant issuedAt,
        List<CameraRecord> cameras) {

    public record CameraRecord(
            String cameraId,
            boolean enabled,
            String protocolType,
            String mainStreamUrl,
            String subStreamUrl,
            Instant expiresAt) {
    }
}
