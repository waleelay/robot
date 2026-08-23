package com.robot.control.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成不含凭据、地址和控制参数的 MQTT 日志摘要。 */
final class MqttLogSummary {

    private static final List<String> SAFE_FIELDS = List.of(
            "commandId", "sessionId", "robotId", "sourceType", "sourceId", "deviceId",
            "channel", "quality", "status", "errorCode", "action", "seq", "callId");
    private static final List<String> SAFE_TARGET_FIELDS = List.of("deviceId", "deviceType");

    private MqttLogSummary() {
    }

    static Map<String, Object> from(ObjectMapper objectMapper, Object payload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (payload == null) {
            summary.put("payloadType", "null");
            return summary;
        }
        summary.put("payloadType", payload.getClass().getSimpleName());
        Map<?, ?> source;
        try {
            source = objectMapper.convertValue(payload, Map.class);
        } catch (IllegalArgumentException ex) {
            return summary;
        }
        copyFields(source, summary, SAFE_FIELDS);
        if (source.get("target") instanceof Map<?, ?> target) {
            Map<String, Object> targetSummary = new LinkedHashMap<>();
            copyFields(target, targetSummary, SAFE_TARGET_FIELDS);
            if (!targetSummary.isEmpty()) {
                summary.put("target", targetSummary);
            }
        }
        return summary;
    }

    private static void copyFields(Map<?, ?> source, Map<String, Object> target, List<String> fields) {
        for (String field : fields) {
            Object value = source.get(field);
            if (value instanceof CharSequence || value instanceof Number
                    || value instanceof Boolean || value instanceof Enum<?>) {
                target.put(field, value);
            }
        }
    }
}
