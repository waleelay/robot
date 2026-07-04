package com.robot.mediaserver.control.dto;

import java.time.OffsetDateTime;

public record MediaEventLogResponse(
        String eventId,
        String sessionId,
        String eventType,
        String eventPayload,
        String traceId,
        OffsetDateTime createdAt) {
}
