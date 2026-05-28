package com.robot.mediaserver.recording.dto;

import java.time.OffsetDateTime;

public record PlaybackUrlResponse(
        String recordingId,
        String playbackType,
        String mimeType,
        String playUrl,
        OffsetDateTime expiresAt) {
}
