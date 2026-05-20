package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.MediaEventLog;
import java.time.OffsetDateTime;

/**
 * 媒体事件日志响应。
 *
 * @author leelay
 * @date 2026/05/20
 */
public record MediaEventLogResponse(
        String eventId,
        String sessionId,
        String eventType,
        String eventPayload,
        String traceId,
        OffsetDateTime createdAt) {

    /**
     * 从事件日志实体构建响应对象。
     *
     * @param eventLog 事件日志实体
     * @return 事件日志响应
     */
    public static MediaEventLogResponse from(MediaEventLog eventLog) {
        return new MediaEventLogResponse(
                eventLog.getEventId(),
                eventLog.getSessionId(),
                eventLog.getEventType(),
                eventLog.getEventPayload(),
                eventLog.getTraceId(),
                eventLog.getCreatedAt());
    }
}
