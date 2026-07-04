package com.robot.control.dto;

import java.time.OffsetDateTime;

public record MediaTrackResponse(
        String trackId,
        String sessionId,
        String trackSid,
        String trackName,
        String participantIdentity,
        String kind,
        VideoChannel channel,
        VideoQuality quality,
        OffsetDateTime publishedAt,
        OffsetDateTime unpublishedAt) {
}
