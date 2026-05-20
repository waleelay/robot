package com.robot.mediaserver.video.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.mediaserver.video.dto.MediaEventLogResponse;
import com.robot.mediaserver.video.model.MediaEventLog;
import com.robot.mediaserver.video.repository.MediaEventLogRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 媒体事件日志服务。
 *
 * <p>统一负责事件落库和 WebSocket 广播，避免状态流转时遗漏日志或前端通知。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
@Service
public class MediaEventLogService {

    private static final Logger log = LoggerFactory.getLogger(MediaEventLogService.class);

    private final MediaEventLogRepository repository;
    private final MediaWebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;

    public MediaEventLogService(
            MediaEventLogRepository repository,
            MediaWebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录并广播媒体事件。
     *
     * @param sessionId 会话 ID
     * @param eventType 事件类型
     * @param payload 事件内容
     */
    public void recordAndPublish(String sessionId, String eventType, Object payload) {
        record(sessionId, eventType, payload);
        webSocketPublisher.publish(eventType, payload);
    }

    /**
     * 记录媒体事件，不进行 WebSocket 广播。
     *
     * @param sessionId 会话 ID
     * @param eventType 事件类型
     * @param payload 事件内容
     */
    public void record(String sessionId, String eventType, Object payload) {
        MediaEventLog eventLog = new MediaEventLog();
        eventLog.setEventId("evt_" + compactUuid());
        eventLog.setSessionId(sessionId);
        eventLog.setEventType(eventType);
        eventLog.setTraceId("trace_" + compactUuid());
        eventLog.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        eventLog.setEventPayload(toJson(payload));
        repository.save(eventLog);
    }

    /**
     * 查询指定会话最近事件。
     *
     * @param sessionId 会话 ID
     * @return 事件日志响应列表
     */
    public List<MediaEventLogResponse> recentEvents(String sessionId) {
        return repository.findTop50BySessionIdOrderByCreatedAtDesc(sessionId).stream()
                .map(MediaEventLogResponse::from)
                .toList();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize media event payload", ex);
            return objectMapper.valueToTree(Map.of("message", "payload serialization failed")).toString();
        }
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
