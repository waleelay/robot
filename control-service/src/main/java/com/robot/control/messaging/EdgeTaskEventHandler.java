package com.robot.control.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.config.DateTimeConfig;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将边缘任务进度和任务控制结果 MQTT 上报转换为大屏任务变化事件。
 */
@Component
public class EdgeTaskEventHandler {

    private static final Logger log = LoggerFactory.getLogger(EdgeTaskEventHandler.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String PANORAMA_TASK_CHANGED = "panorama.task.changed";

    private final ObjectMapper objectMapper;
    private final MediaWebSocketPublisher webSocketPublisher;

    public EdgeTaskEventHandler(ObjectMapper objectMapper, MediaWebSocketPublisher webSocketPublisher) {
        this.objectMapper = objectMapper;
        this.webSocketPublisher = webSocketPublisher;
    }

    /**
     * 处理 {@code eiop/v1/edge/{serialNumber}/tasks/progress} 任务进度消息。
     *
     * @param topic MQTT topic
     * @param json MQTT payload
     */
    public void handleProgress(String topic, String json) {
        try {
            String serialNumber = serialNumberFromTaskTopic(topic, "progress");
            Map<String, Object> envelope = objectMapper.readValue(json, MAP_TYPE);
            String messageType = string(envelope.get("messageType"));
            if (!messageType.isBlank() && !"TASK_PROGRESS_REPORT".equals(messageType)) {
                log.debug("Ignore unsupported edge task progress type={} topic={}", messageType, topic);
                return;
            }
            Map<String, Object> payload = map(envelope.get("payload"));
            if (payload.isEmpty()) {
                log.debug("Ignore edge task progress without payload topic={}", topic);
                return;
            }
            webSocketPublisher.publish(PANORAMA_TASK_CHANGED, progressData(serialNumber, envelope, payload));
        } catch (Exception ex) {
            log.warn("Failed to handle edge task progress topic={}, payload={}", topic, json, ex);
        }
    }

    /**
     * 处理 {@code eiop/v1/edge/{serialNumber}/tasks/control-results} 任务控制结果消息。
     *
     * @param topic MQTT topic
     * @param json MQTT payload
     */
    public void handleControlResult(String topic, String json) {
        try {
            String serialNumber = serialNumberFromTaskTopic(topic, "control-results");
            Map<String, Object> envelope = objectMapper.readValue(json, MAP_TYPE);
            String messageType = string(envelope.get("messageType"));
            if (!messageType.isBlank() && !"TASK_CONTROL_RESULT_REPORT".equals(messageType)) {
                log.debug("Ignore unsupported edge task control result type={} topic={}", messageType, topic);
                return;
            }
            Map<String, Object> payload = map(envelope.get("payload"));
            if (payload.isEmpty()) {
                log.debug("Ignore edge task control result without payload topic={}", topic);
                return;
            }
            webSocketPublisher.publish(PANORAMA_TASK_CHANGED, controlResultData(serialNumber, envelope, payload));
        } catch (Exception ex) {
            log.warn("Failed to handle edge task control result topic={}, payload={}", topic, json, ex);
        }
    }

    private Map<String, Object> progressData(
            String serialNumber,
            Map<String, Object> envelope,
            Map<String, Object> payload) {
        Map<String, Object> data = baseData(serialNumber, envelope, payload);
        String status = normalizeTaskStatus(payload.get("status"));
        putNullable(data, "status", status);
        putNullable(data, "statusName", taskStatusName(status));
        putIfPresent(data, "programKey", payload.get("programKey"));
        putIfPresent(data, "nodeId", payload.get("nodeId"));
        putIfPresent(data, "actionRef", payload.get("actionRef"));
        putIfPresent(data, "resultCode", payload.get("resultCode"));
        putIfPresent(data, "message", payload.get("message"));
        Map<String, Object> currentLocation = map(payload.get("currentLocation"));
        if (!currentLocation.isEmpty()) {
            data.put("location", currentLocation);
            data.put("currentLocation", formatLocation(currentLocation));
        }
        data.put("edgeTaskPayload", new LinkedHashMap<>(payload));
        return data;
    }

    private Map<String, Object> controlResultData(
            String serialNumber,
            Map<String, Object> envelope,
            Map<String, Object> payload) {
        Map<String, Object> data = baseData(serialNumber, envelope, payload);
        String status = normalizeTaskStatus(payload.get("status"));
        putNullable(data, "status", status);
        putNullable(data, "statusName", taskStatusName(status));
        putIfPresent(data, "controlCommandId", payload.get("controlCommandId"));
        putIfPresent(data, "controlResult", payload.get("result"));
        putIfPresent(data, "errorCode", payload.get("errorCode"));
        putIfPresent(data, "message", payload.get("message"));
        data.put("edgeTaskPayload", new LinkedHashMap<>(payload));
        return data;
    }

    private Map<String, Object> baseData(
            String serialNumber,
            Map<String, Object> envelope,
            Map<String, Object> payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        putNullable(data, "taskId", firstValue(payload, "taskId", "planId", "workflowPlanId"));
        putNullable(data, "workflowInstanceId", firstValue(payload,
                "taskInstanceId", "workflowInstanceId", "taskWorkflowInstanceId", "workflowExecutionId"));
        putIfPresent(data, "commandId", payload.get("commandId"));
        data.put("robotId", serialNumber);
        putIfPresent(data, "updatedAt", DateTimeConfig.normalize(envelope.get("timestamp")));
        putIfPresent(data, "messageId", envelope.get("messageId"));
        putIfPresent(data, "schemaVersion", envelope.get("schemaVersion"));
        data.put("source", "EDGE_TASK_MQTT");
        return data;
    }

    private String serialNumberFromTaskTopic(String topic, String suffix) {
        String[] parts = topic == null ? new String[0] : topic.split("/");
        if (parts.length != 6
                || !"eiop".equals(parts[0])
                || !"v1".equals(parts[1])
                || !"edge".equals(parts[2])
                || parts[3].isBlank()
                || !"tasks".equals(parts[4])
                || !suffix.equals(parts[5])) {
            throw new IllegalArgumentException("无效的边缘任务 topic：" + topic);
        }
        return parts[3];
    }

    private String normalizeTaskStatus(Object value) {
        String status = string(value).trim();
        if (status.isBlank()) {
            return null;
        }
        String normalized = status.toUpperCase(Locale.ROOT);
        if (normalized.contains("RUNNING") || normalized.contains("EXECUTING") || normalized.contains("执行中")) {
            return "running";
        }
        if (normalized.contains("COMPLETED") || normalized.contains("FINISHED") || normalized.contains("已完成")) {
            return "completed";
        }
        if (normalized.contains("PAUSED") || normalized.contains("暂停")) {
            return "paused";
        }
        if (normalized.contains("FAILED") || normalized.contains("ERROR") || normalized.contains("失败")) {
            return "failed";
        }
        if (normalized.contains("TERMINATED") || normalized.contains("CANCELED") || normalized.contains("终止")) {
            return "terminated";
        }
        if (normalized.contains("IDLE") || normalized.contains("PENDING") || normalized.contains("WAITING")
                || normalized.contains("待执行")) {
            return "pending";
        }
        return status;
    }

    private String taskStatusName(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status) {
            case "running" -> "执行中";
            case "completed" -> "已完成";
            case "paused" -> "已暂停";
            case "failed" -> "执行失败";
            case "terminated" -> "已终止";
            case "pending" -> "待执行";
            default -> status;
        };
    }

    private String formatLocation(Map<String, Object> location) {
        Object x = location.get("x");
        Object y = location.get("y");
        if (x == null || y == null) {
            return null;
        }
        return "x:" + x + ",y:" + y;
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private void putNullable(Map<String, Object> target, String key, Object value) {
        target.put(key, value == null || String.valueOf(value).isBlank() ? null : value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
