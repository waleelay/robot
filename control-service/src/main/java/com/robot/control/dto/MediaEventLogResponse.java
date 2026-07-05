package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 媒体事件日志响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param eventId 事件 ID
 * @param sessionId 会话 ID
 * @param eventType 事件类型
 * @param eventPayload 事件载荷
 * @param traceId 链路追踪 ID
 * @param createdAt 创建时间
 */
public record MediaEventLogResponse(
        String eventId,
        String sessionId,
        String eventType,
        String eventPayload,
        String traceId,
        OffsetDateTime createdAt) {
}
