package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.bigscreen.panorama.StatsPart;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** 将下游实时消息转换为大屏事件，保持失效通知及资源标识语义。 */
@Component
public class PanoramaWebSocketEventAdapter {

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROBOT_STATE_EVENT = "robot.state";
    private static final String ROBOT_MILEAGE_CHANGED = "robot.mileage.changed";
    private static final String STATE_SOURCE_CLIENT = "MEDIA_CLIENT_STATUS";
    private static final String STATE_SOURCE_GIS_ENRICHMENT = "GIS_LOCATION_ENRICHMENT";
    private static final String PANORAMA_DEVICE_STATUS_CHANGED = "panorama.device.status.changed";
    private static final String PANORAMA_DEVICE_LOCATION_CHANGED = "panorama.device.location.changed";
    private static final String PANORAMA_TASK_CHANGED = "panorama.task.changed";
    private static final String PANORAMA_ALARM_CHANGED = "panorama.alarm.changed";
    private static final String MANAGEMENT_TASK_INVALIDATED = "management.task.invalidated";
    private static final String MANAGEMENT_ALARM_INVALIDATED = "management.alarm.invalidated";
    private static final String FIXED_CAMERA_HEALTH_CHANGED = "fixed-camera.health.changed";
    private static final Set<String> TASK_EVENTS = Set.of(
            "task.changed",
            "task.created",
            "task.updated",
            "task.deleted",
            "management.task.changed",
            "management.task.updated");
    private static final Set<String> ALARM_EVENTS = Set.of(
            "alarm.changed",
            "alarm.created",
            "alarm.updated",
            "alarm.disposed",
            "management.alarm.changed",
            "management.alarm.updated");
    private static final Set<String> DEVICE_EVENTS = Set.of(
            "device.created",
            "device.updated",
            "device.deleted",
            "management.device.created",
            "management.device.updated",
            "management.device.deleted");
    private final ObjectMapper objectMapper;
    /**
     * 按浏览器连接和机器人保存最近在线状态，避免重复触发设备统计刷新。
     */
    private final Map<String, Map<String, String>> robotStatusesBySession = new ConcurrentHashMap<>();

    /**
     * 初始化 PanoramaWebSocketEventAdapter，保存所需依赖及初始运行状态。
     *
     * @param objectMapper JSON 编解码器
     */
    public PanoramaWebSocketEventAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把上游事件转换为大屏协议并执行当前连接所需的状态合并。
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 原始消息及转换后的大屏消息；是否投递由上层权限和事件规则决定
     */
    public List<String> adapt(String centerPayload) {
        List<String> messages = new ArrayList<>();
        messages.add(centerPayload);

        JsonNode root = readTree(centerPayload);
        if (root == null) {
            return messages;
        }

        String event = text(root, "event");
        JsonNode data = root.path("data");
        // Control 原始健康事件含 gatewayId/cameraId，不能直接广播给其他用户；
        // statsRefreshParts 只按当前身份重算设备统计，不刷新 Overview。
        if (FIXED_CAMERA_HEALTH_CHANGED.equals(event)) {
            return List.of();
        }
        if (!data.isObject()) {
            return messages;
        }
        // 失效通知由 BFF 补查后再通知页面，避免原始通知和快照重复触发查询。
        if (MANAGEMENT_ALARM_INVALIDATED.equals(event) || MANAGEMENT_TASK_INVALIDATED.equals(event)) {
            return List.of();
        }

        // 旧版 Control 直接转发边缘任务进度时无法得到任务计划 ID。残缺事件不下发给前端，
        // 由 BigscreenWebSocketBridgeHandler 触发管理端快照刷新后生成完整任务事件。
        if (isUnresolvedPanoramaTask(event, data)) {
            return List.of();
        }

        if (ROBOT_STATE_EVENT.equals(event)) {
            appendRobotStateEvents(messages, root, data);
        } else if (TASK_EVENTS.contains(event)) {
            messages.add(writePanoramaTask(root, data));
        } else if (ALARM_EVENTS.contains(event)) {
            messages.add(writePanoramaAlarm(root, data));
        }
        return messages;
    }

    /**
     * 判断上游消息是否要求重新查询任务事实。
     *
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 是否属于任务失效通知
     */
    public boolean isTaskInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        if (root == null) {
            return false;
        }
        String event = text(root, "event");
        return MANAGEMENT_TASK_INVALIDATED.equals(event)
                || isUnresolvedPanoramaTask(event, root.path("data"));
    }

    /**
     * 从任务事件中提取合并与去重标识。
     *
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 任务失效事件键；无法提取时为空
     */
    public String taskInvalidationKey(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        if (root == null || !MANAGEMENT_TASK_INVALIDATED.equals(text(root, "event"))) {
            return null;
        }
        JsonNode data = root.path("data");
        String source = text(data, "source");
        String eventId = text(data, "eventId");
        return source.isBlank() || eventId.isBlank() ? null : source + ":" + eventId;
    }

    /**
     * 判断上游消息是否要求重新查询告警事实。
     *
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 是否属于告警失效通知
     */
    public boolean isAlarmInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        return root != null && MANAGEMENT_ALARM_INVALIDATED.equals(text(root, "event"));
    }

    /**
     * 从告警事件中提取合并与去重标识。
     *
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 告警失效事件键；无法提取时为空
     */
    public String alarmInvalidationKey(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        if (root == null || !MANAGEMENT_ALARM_INVALIDATED.equals(text(root, "event"))) {
            return null;
        }
        JsonNode data = root.path("data");
        String source = text(data, "source");
        String eventId = text(data, "eventId");
        return source.isBlank() || eventId.isBlank() ? null : source + ":" + eventId;
    }

    private boolean isUnresolvedPanoramaTask(String event, JsonNode data) {
        if (!PANORAMA_TASK_CHANGED.equals(event) || data == null || !data.isObject()) {
            return false;
        }
        if (hasAny(data, "taskId")) {
            return false;
        }
        JsonNode task = data.path("task");
        return !task.isObject() || !hasAny(task, "taskId");
    }

    private void appendRobotStateEvents(List<String> messages, JsonNode root, JsonNode data) {
        String robotId = text(data, "robotId");
        if (robotId.isBlank()) {
            return;
        }

        String stateSource = text(data, "stateSource");
        if (!STATE_SOURCE_CLIENT.equals(stateSource) && !STATE_SOURCE_GIS_ENRICHMENT.equals(stateSource)) {
            messages.add(writePanoramaDeviceStatus(root, data));
        }

        JsonNode location = firstObject(data, "location", "localization");
        if (location == null) {
            location = objectAt(data, "status", "localization");
        }
        if (hasLocation(location)) {
            messages.add(writePanoramaDeviceLocation(root, robotId, location));
        }

        JsonNode task = firstObject(data, "task", "currentTask");
        if (task == null) {
            task = objectAt(data, "status", "task");
        }
        if (task != null && hasAny(task, "taskId", "taskInstanceId", "id")) {
            messages.add(writePanoramaTask(root, task));
        }
    }

    private String writePanoramaDeviceStatus(JsonNode sourceRoot, JsonNode sourceData) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("robotId", text(sourceData, "robotId"));
        data.put("status", panoramaDeviceStatus(sourceData));
        String statusChangedAt = text(sourceData, "statusChangedAt");
        data.put("statusChangedAt", statusChangedAt.isBlank() ? timestamp(sourceRoot) : statusChangedAt);
        putNullableText(data, "runtimeUpdatedAt", sourceData.get("runtimeUpdatedAt"));
        putNullableLong(data, "stateSeq", sourceData.get("stateSeq"));
        putNullableInt(data, "battery", sourceData.get("battery"));
        String controlMode = normalizeControlMode(text(sourceData, "controlMode"));
        data.put("controlMode", controlMode);
        data.put("controlModeName", controlModeName(controlMode));
        putNullableNumber(data, "speed", firstExisting(sourceData, "speed", "currentSpeed"));
        putNullableText(data, "runningStatus", sourceData.get("runningStatus"));
        putNullableText(data, "healthStatus", sourceData.get("healthStatus"));
        putNullableBoolean(data, "charging", sourceData.get("charging"));
        putNullableText(data, "chargingStatus", sourceData.get("chargingStatus"));
        putNullableText(data, "taskStatus", sourceData.get("taskStatus"));
        putNullableText(data, "missionStatus", sourceData.get("missionStatus"));
        putNullableBoolean(data, "moving", sourceData.get("moving"));
        putNullableBoolean(data, "estopActive", sourceData.get("estopActive"));
        JsonNode location = firstObject(sourceData, "location", "localization");
        if (location != null) {
            data.set("edgeLocation", location);
        }

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_STATUS_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private String writePanoramaDeviceLocation(JsonNode sourceRoot, String robotId, JsonNode sourceLocation) {
        ObjectNode location = objectMapper.createObjectNode();
        putNullableNumber(location, "lng", firstExisting(sourceLocation, "lng", "longitude"));
        putNullableNumber(location, "lat", firstExisting(sourceLocation, "lat", "latitude"));
        putNullableNumber(location, "altitude", sourceLocation.get("altitude"));
        putNullableNumber(location, "x", firstExisting(sourceLocation, "x", "coordinateX"));
        putNullableNumber(location, "y", firstExisting(sourceLocation, "y", "coordinateY"));
        putNullableNumber(location, "z", firstExisting(sourceLocation, "z", "coordinateZ"));
        putNullableNumber(location, "yaw", sourceLocation.get("yaw"));
        putNullableText(location, "mapId", sourceLocation.get("mapId"));
        putNullableText(location, "coordinateType", sourceLocation.get("coordinateType"));
        putNullableBoolean(location, "localized", sourceLocation.get("localized"));
        putNullableText(location, "address", sourceLocation.get("address"));
        String updatedAt = firstText(sourceLocation, "updatedAt", "reportedAt", "receivedAt");
        location.put("updatedAt", updatedAt.isBlank() ? timestamp(sourceRoot) : updatedAt);

        ObjectNode data = objectMapper.createObjectNode();
        data.put("robotId", robotId);
        data.set("location", location);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_LOCATION_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private String writePanoramaTask(JsonNode sourceRoot, JsonNode sourceData) {
        JsonNode sourceTask = firstObject(sourceData, "task", "payload");
        if (sourceTask == null) {
            sourceTask = sourceData;
        }
        String taskId = firstText(sourceTask, "taskId", "taskInstanceId", "id");

        ObjectNode task = objectMapper.createObjectNode();
        putNullableText(task, "taskId", textNode(taskId));
        putNullableText(task, "name", firstExisting(sourceTask, "name", "taskName"));
        putNullableText(task, "executionStatus", sourceTask.get("executionStatus"));
        putNullableText(task, "timeRange", sourceTask.get("timeRange"));
        putNullableText(task, "currentLocation", sourceTask.get("currentLocation"));

        ObjectNode data = objectMapper.createObjectNode();
        putNullableText(data, "taskId", textNode(taskId));
        data.set("task", task);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_TASK_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private String writePanoramaAlarm(JsonNode sourceRoot, JsonNode sourceData) {
        JsonNode sourceAlarm = firstObject(sourceData, "alarm", "payload");
        if (sourceAlarm == null) {
            sourceAlarm = sourceData;
        }
        String alarmId = firstText(sourceAlarm, "alarmId", "id", "alarmCode");

        ObjectNode data = objectMapper.createObjectNode();
        putNullableText(data, "alarmId", textNode(alarmId));
        if (sourceData.has("summary")) {
            data.set("summary", sourceData.get("summary"));
        }
        data.set("alarm", sourceAlarm.deepCopy());

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_ALARM_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    /**
     * 按事件影响范围确定需要刷新的统计块。
     *
     * @param sessionId 会话 ID
     * @param centerPayload 上游实时事件 JSON 正文
     * @return 本次需要重新聚合的统计分块
     */
    public Set<StatsPart> statsRefreshParts(String sessionId, String centerPayload) {
        JsonNode root = readTree(centerPayload);
        if (root == null || !root.path("data").isObject()) {
            return Set.of();
        }
        String event = text(root, "event");
        Set<StatsPart> parts = new HashSet<>();
        if (ALARM_EVENTS.contains(event) || MANAGEMENT_ALARM_INVALIDATED.equals(event)) {
            parts.add(StatsPart.ALARMS);
        }
        if (TASK_EVENTS.contains(event) || MANAGEMENT_TASK_INVALIDATED.equals(event)
                || ROBOT_MILEAGE_CHANGED.equals(event)) {
            parts.add(StatsPart.TASKS);
        }
        if (DEVICE_EVENTS.contains(event) || FIXED_CAMERA_HEALTH_CHANGED.equals(event)) {
            parts.add(StatsPart.DEVICES);
        }
        if (!ROBOT_STATE_EVENT.equals(event)) {
            return parts;
        }
        JsonNode data = root.path("data");
        String robotId = text(data, "robotId");
        String stateSource = text(data, "stateSource");
        if (robotId.isBlank()
                || STATE_SOURCE_CLIENT.equals(stateSource)
                || STATE_SOURCE_GIS_ENRICHMENT.equals(stateSource)) {
            return parts;
        }
        String status = panoramaDeviceStatus(data);
        Map<String, String> robotStatuses = robotStatusesBySession.computeIfAbsent(
                sessionId, ignored -> new ConcurrentHashMap<>());
        if (!status.equalsIgnoreCase(robotStatuses.put(robotId, status))) {
            parts.add(StatsPart.DEVICES);
            parts.add(StatsPart.TASKS);
        }
        return parts;
    }

    /**
     * 移除指定浏览器会话的机器人在线状态适配缓存。
     * @param sessionId 会话 ID
     */
    public void removeSession(String sessionId) {
        robotStatusesBySession.remove(sessionId);
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String writeValue(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize panorama websocket event", exception);
        }
    }

    private String timestamp(JsonNode sourceRoot) {
        String timestamp = text(sourceRoot, "timestamp");
        return timestamp.isBlank() ? EVENT_TIME_FORMATTER.format(LocalDateTime.now()) : timestamp;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private JsonNode firstExisting(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.get(firstField);
        return first == null || first.isNull() ? node.get(secondField) : first;
    }

    private JsonNode firstObject(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.get(firstField);
        if (first != null && first.isObject()) {
            return first;
        }
        JsonNode second = node.get(secondField);
        return second != null && second.isObject() ? second : null;
    }

    private JsonNode objectAt(JsonNode node, String firstField, String secondField) {
        JsonNode first = node.get(firstField);
        if (first == null || !first.isObject()) {
            return null;
        }
        JsonNode second = first.get(secondField);
        return second != null && second.isObject() ? second : null;
    }

    private boolean hasLocation(JsonNode location) {
        return location != null && hasAny(
                location, "lng", "longitude", "lat", "latitude", "x", "coordinateX", "address", "localized");
    }

    private boolean hasAny(JsonNode node, String... fieldNames) {
        if (node == null) {
            return false;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode textNode(String value) {
        return value == null || value.isBlank() ? null : objectMapper.getNodeFactory().textNode(value);
    }

    private String normalizeControlMode(String controlMode) {
        String mode = controlMode == null ? "" : controlMode.trim();
        if ("导航模式".equals(mode)) {
            return mode;
        }
        return "手动模式".equals(mode) || "常规模式".equals(mode) ? "手动模式" : null;
    }

    private String panoramaDeviceStatus(JsonNode sourceData) {
        String status = text(sourceData, "status");
        if ("offline".equalsIgnoreCase(status)) {
            return "offline";
        }
        String healthStatus = text(sourceData, "healthStatus").toUpperCase(Locale.ROOT);
        if (healthStatus.contains("ERROR")
                || healthStatus.contains("FAULT")
                || healthStatus.contains("异常")
                || healthStatus.contains("故障")) {
            return "fault";
        }
        return status.isBlank() ? "online" : status;
    }

    private String controlModeName(String controlMode) {
        return controlMode;
    }

    private void putNullableInt(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value.asInt());
    }

    private void putNullableLong(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value.asLong());
    }

    private void putNullableText(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value.asText());
    }

    private void putNullableNumber(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value.asDouble());
    }

    private void putNullableBoolean(ObjectNode target, String fieldName, JsonNode value) {
        if (value == null || value.isNull() || !value.isBoolean()) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value.asBoolean());
    }
}
