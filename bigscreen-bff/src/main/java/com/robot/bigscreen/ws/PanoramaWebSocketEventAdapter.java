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
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class PanoramaWebSocketEventAdapter {

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROBOT_STATE_EVENT = "robot.state";
    private static final String ROBOT_MILEAGE_CHANGED = "robot.mileage.changed";
    private static final String STATE_SOURCE_CLIENT = "MEDIA_CLIENT_STATUS";
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
    private static final double[][] TEST111_LOCATION_POINTS = {
            {-1.481845, -1.893522, -0.02789},
            {-1.621149, -8.08522, -0.025462},
            {1.4151, -7.861758, -0.044444}
    };

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> robotStatusesBySession = new ConcurrentHashMap<>();
    private final AtomicLong locationTick = new AtomicLong();
    private final AtomicLong test111LocationTick = new AtomicLong();

    public PanoramaWebSocketEventAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> adapt(String centerPayload) {
        List<String> messages = new ArrayList<>();
        messages.add(centerPayload);

        JsonNode root = readTree(centerPayload);
        if (root == null) {
            return messages;
        }

        String event = text(root, "event");
        JsonNode data = root.path("data");
        // Control 原始健康事件含 gatewayId/cameraId，不能直接广播给其他用户。
        // Bridge 会改发不含资源明细的用户范围失效事件，并按当前身份重查 Overview。
        if (FIXED_CAMERA_HEALTH_CHANGED.equals(event)) {
            return List.of();
        }
        if (!data.isObject()) {
            return messages;
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

    public boolean isTaskInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        if (root == null) {
            return false;
        }
        String event = text(root, "event");
        return MANAGEMENT_TASK_INVALIDATED.equals(event)
                || isUnresolvedPanoramaTask(event, root.path("data"));
    }

    public boolean isAlarmInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        return root != null && MANAGEMENT_ALARM_INVALIDATED.equals(text(root, "event"));
    }

    public boolean isFixedCameraHealthInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        return root != null && FIXED_CAMERA_HEALTH_CHANGED.equals(text(root, "event"));
    }

    public String fixedCameraHealthInvalidation(String centerPayload) {
        JsonNode root = readTree(centerPayload);
        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", "bigscreen.fixed-camera.health.changed");
        event.put("timestamp", root == null ? EVENT_TIME_FORMATTER.format(LocalDateTime.now()) : timestamp(root));
        event.set("data", objectMapper.createObjectNode().put("reason", "FIXED_CAMERA_HEALTH_CHANGED"));
        return writeValue(event);
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

        if (!STATE_SOURCE_CLIENT.equals(text(data, "stateSource"))) {
            messages.add(writePanoramaDeviceStatus(root, data));
        }

        JsonNode location = firstObject(data, "location", "localization");
        if (location == null) {
            location = objectAt(data, "status", "localization");
        }
        if (hasLocation(location)) {
            messages.add(writePanoramaDeviceLocation(root, robotId, location));
        } else if ("test111".equals(robotId)) {
            messages.add(writeTest111PanoramaDeviceLocation(root, robotId, test111LocationTick.getAndIncrement()));
        } else if ("SN005".equals(robotId)) {
            messages.add(writeMockPanoramaDeviceLocation2(root, robotId, locationTick.incrementAndGet()));
        } else if ("SN006".equals(robotId)) {
            messages.add(writeMockPanoramaDeviceLocation(root, robotId, locationTick.incrementAndGet()));
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
        putNullableInt(data, "battery", sourceData.get("battery"));
        String controlMode = normalizeControlMode(text(sourceData, "controlMode"));
        data.put("controlMode", controlMode);
        data.put("controlModeName", controlModeName(controlMode));
        putNullableNumber(data, "speed", firstExisting(sourceData, "speed", "currentSpeed"));
        putNullableText(data, "runningStatus", sourceData.get("runningStatus"));
        putNullableText(data, "healthStatus", sourceData.get("healthStatus"));
        putNullableText(data, "chargingStatus", sourceData.get("chargingStatus"));
        putNullableText(data, "missionStatus", sourceData.get("missionStatus"));
        putNullableBoolean(data, "moving", sourceData.get("moving"));
        putNullableBoolean(data, "estopActive", sourceData.get("estopActive"));

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_STATUS_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private String writeMockPanoramaDeviceLocation(JsonNode sourceRoot, String robotId, long currentTick) {
        long step = currentTick % 6;
        double offset = step * 0.00003;

        ObjectNode location = objectMapper.createObjectNode();
        location.put("lng", 106.03655278081857 + offset);
        location.put("lat", 30.7478613352993 + offset);
        location.putNull("altitude");
        location.put("x", 118.4 + step * 0.6);
        location.put("y", 42.8 + step * 0.4);
        location.put("z", 0.0);
        location.put("address", "A区主干道-" + robotId);
        location.put("updatedAt", timestamp(sourceRoot));

        ObjectNode data = objectMapper.createObjectNode();
        data.put("robotId", robotId);
        data.set("location", location);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_LOCATION_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }
    private String writeMockPanoramaDeviceLocation2(JsonNode sourceRoot, String robotId, long currentTick) {
        long step = currentTick % 6;
        double offset = step * 0.00003;

        ObjectNode location = objectMapper.createObjectNode();
        location.put("lng", 106.03655278081857 + offset);
        location.put("lat", 30.7478613352993 + offset);
        location.putNull("altitude");
        location.put("x", 1.4151);
        location.put("y", -7.861758);
        location.put("z", -0.044444);
        location.put("address", "A区主干道-" + robotId);
        location.put("updatedAt", timestamp(sourceRoot));

        ObjectNode data = objectMapper.createObjectNode();
        data.put("robotId", robotId);
        data.set("location", location);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_LOCATION_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private String writeTest111PanoramaDeviceLocation(JsonNode sourceRoot, String robotId, long currentTick) {
        int index = Math.floorMod(currentTick, TEST111_LOCATION_POINTS.length);
        double[] point = TEST111_LOCATION_POINTS[index];

        ObjectNode location = objectMapper.createObjectNode();
        location.put("lng", 106.03655278081857);
        location.put("lat", 30.7478613352993);
        location.putNull("altitude");
        location.put("x", point[0]);
        location.put("y", point[1]);
        location.put("z", point[2]);
        location.put("address", "A区主干道-" + robotId);
        location.put("updatedAt", timestamp(sourceRoot));

        ObjectNode data = objectMapper.createObjectNode();
        data.put("robotId", robotId);
        data.set("location", location);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_DEVICE_LOCATION_CHANGED);
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
        putNullableText(location, "coordinateType", sourceLocation.get("coordinateType"));
        putNullableText(location, "mapId", sourceLocation.get("mapId"));
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
        putNullableText(task, "status", firstExisting(sourceTask, "status", "taskStatus"));
        putNullableText(task, "statusName", sourceTask.get("statusName"));
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
        if (robotId.isBlank() || STATE_SOURCE_CLIENT.equals(text(data, "stateSource"))) {
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
        if ("导航模式".equals(mode)) return mode;
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
