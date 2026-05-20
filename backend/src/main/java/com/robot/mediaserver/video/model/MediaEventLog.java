package com.robot.mediaserver.video.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 媒体会话事件日志实体。
 *
 * <p>用于记录实时视频会话的关键状态事件，便于联调排障和后续审计追踪。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Entity
@Table(
        name = "media_event_log",
        indexes = {
                @Index(name = "idx_media_event_session_time", columnList = "sessionId,createdAt"),
                @Index(name = "idx_media_event_type_time", columnList = "eventType,createdAt")
        })
public class MediaEventLog {

    @Id
    @Column(length = 64)
    private String eventId;

    @Column(length = 64)
    private String sessionId;

    @Column(nullable = false, length = 96)
    private String eventType;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String eventPayload;

    @Column(length = 64)
    private String traceId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventPayload() {
        return eventPayload;
    }

    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
