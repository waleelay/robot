package com.robot.control.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成不含凭据、地址和控制参数的 MQTT 日志摘要。 */
final class MqttLogSummary {

    /**
     * 允许进入 MQTT 日志的顶层协议字段白名单；新增字段前必须核对是否包含凭据或控制参数。
     */
    private static final List<String> SAFE_FIELDS = List.of(
            "commandId", "sessionId", "robotId", "sourceType", "sourceId", "deviceId",
            "channel", "quality", "status", "errorCode", "action", "seq", "callId");
    /**
     * 允许记录的控制目标标识字段，不记录目标中的地址或其他配置。
     */
    private static final List<String> SAFE_TARGET_FIELDS = List.of("deviceId", "deviceType");
    /** 只转换日志显示标签；读取载荷时仍使用原协议字段。 */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("commandId", "命令标识"), Map.entry("sessionId", "会话标识"),
            Map.entry("robotId", "机器人标识"), Map.entry("sourceType", "来源类型"),
            Map.entry("sourceId", "来源标识"), Map.entry("deviceId", "设备标识"),
            Map.entry("channel", "通道"), Map.entry("quality", "清晰度"),
            Map.entry("status", "状态"), Map.entry("errorCode", "错误码"),
            Map.entry("action", "动作"), Map.entry("seq", "序号"),
            Map.entry("callId", "呼叫标识"), Map.entry("deviceType", "设备类型"));

    private MqttLogSummary() {
    }

    static Map<String, Object> from(ObjectMapper objectMapper, Object payload) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (payload == null) {
            summary.put("载荷类型", "空载荷");
            return summary;
        }
        summary.put("载荷类型", payload.getClass().getSimpleName());
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
                summary.put("目标设备", targetSummary);
            }
        }
        return summary;
    }

    private static void copyFields(Map<?, ?> source, Map<String, Object> target, List<String> fields) {
        for (String field : fields) {
            Object value = source.get(field);
            if (value instanceof CharSequence || value instanceof Number
                    || value instanceof Boolean || value instanceof Enum<?>) {
                target.put(FIELD_LABELS.get(field), value);
            }
        }
    }
}
