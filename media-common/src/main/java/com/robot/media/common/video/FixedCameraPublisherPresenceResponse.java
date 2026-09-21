package com.robot.media.common.video;

import java.time.OffsetDateTime;

/** 固定摄像头发布 Participant 与视频 Track 的实时存在性。 */
public record FixedCameraPublisherPresenceResponse(
        String cameraId,
        boolean participantPresent,
        boolean trackPresent,
        OffsetDateTime observedAt) {
}
