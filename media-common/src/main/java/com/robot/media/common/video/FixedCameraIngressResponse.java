package com.robot.media.common.video;

import java.time.OffsetDateTime;

/** 固定摄像头 LiveKit Ingress 配置与状态。 */
public record FixedCameraIngressResponse(
        String cameraId,
        String ingressId,
        VideoPublisherMode publisherMode,
        long publisherRevision,
        long ingressOperationRevision,
        boolean configured,
        String roomName,
        String participantIdentity,
        String streamStatus,
        String reasonCode,
        OffsetDateTime observedAt,
        boolean credentialIssued,
        String url,
        String streamKey) {
}
