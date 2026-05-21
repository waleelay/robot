package com.robot.mediaserver.source.dto;

import com.robot.mediaserver.source.model.MediaSource;
import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import java.time.OffsetDateTime;

public record MediaSourceResponse(
        String sourceId,
        String robotId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        String rtspUrl,
        boolean enabled,
        String name,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MediaSourceResponse from(MediaSource source) {
        return new MediaSourceResponse(
                source.getSourceId(),
                source.getRobotId(),
                source.getDeviceId(),
                source.getChannel(),
                source.getQuality(),
                source.getRtspUrl(),
                source.isEnabled(),
                source.getName(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }
}
