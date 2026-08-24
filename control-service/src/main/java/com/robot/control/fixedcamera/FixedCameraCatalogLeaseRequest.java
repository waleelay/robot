package com.robot.control.fixedcamera;

import java.time.Instant;
import java.util.List;

/** BFF 使用当前用户授权列表生成的固定摄像头短租约。 */
public record FixedCameraCatalogLeaseRequest(
        String leaseId,
        long version,
        Instant issuedAt,
        Instant expiresAt,
        List<CameraRecord> cameras) {

    public record CameraRecord(
            String cameraId,
            boolean enabled,
            String protocolType,
            String mainStreamUrl,
            String subStreamUrl) {
    }
}
