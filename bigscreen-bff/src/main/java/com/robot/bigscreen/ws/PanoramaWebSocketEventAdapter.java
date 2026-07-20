package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class PanoramaWebSocketEventAdapter {

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROBOT_STATE_EVENT = "robot.state";
    private static final String PANORAMA_DEVICE_STATUS_CHANGED = "panorama.device.status.changed";
    private static final String PANORAMA_DEVICE_LOCATION_CHANGED = "panorama.device.location.changed";
    private static final String PANORAMA_TASK_CHANGED = "panorama.task.changed";
    private static final String PANORAMA_ALARM_CHANGED = "panorama.alarm.changed";
    private static final String PANORAMA_STATS_CHANGED = "panorama.stats.changed";
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
    private static final double[][] TEST111_LOCATION_POINTS = {
            {-1.481845, -1.893522, -0.02789},
            {-1.621149, -8.08522, -0.025462},
            {1.4151, -7.861758, -0.044444}
    };

    private final ObjectMapper objectMapper;
    private final Map<String, String> robotStatuses = new ConcurrentHashMap<>();
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
        if (!data.isObject()) {
            return messages;
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

    private void appendRobotStateEvents(List<String> messages, JsonNode root, JsonNode data) {
        String robotId = text(data, "robotId");
        if (robotId.isBlank()) {
            return;
        }

        messages.add(writePanoramaDeviceStatus(root, data));
        updateRobotStatus(robotId, text(data, "status"));
        messages.add(writePanoramaStats(root));

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
        data.put("status", text(sourceData, "status"));
        putNullableInt(data, "battery", sourceData.get("battery"));
        data.put("controlMode", text(sourceData, "controlMode"));
        putNullableNumber(data, "speed", firstExisting(sourceData, "speed", "currentSpeed"));

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

    private String writePanoramaStats(JsonNode sourceRoot) {
        long online = robotStatuses.values().stream().filter("online"::equalsIgnoreCase).count();
        long offline = robotStatuses.values().stream().filter("offline"::equalsIgnoreCase).count();
        long fault = robotStatuses.values().stream().filter("fault"::equalsIgnoreCase).count();

        ObjectNode deviceStats = objectMapper.createObjectNode();
        deviceStats.put("total", robotStatuses.size());
        deviceStats.put("online", online);
        deviceStats.put("fault", fault);
        deviceStats.put("offline", offline);

        ObjectNode data = objectMapper.createObjectNode();
        data.set("deviceStats", deviceStats);

        ObjectNode event = objectMapper.createObjectNode();
        event.put("event", PANORAMA_STATS_CHANGED);
        event.put("timestamp", timestamp(sourceRoot));
        event.set("data", data);
        return writeValue(event);
    }

    private void updateRobotStatus(String robotId, String status) {
        if (!status.isBlank()) {
            robotStatuses.put(robotId, status);
        }
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
        return location != null && hasAny(location, "lng", "longitude", "lat", "latitude", "x", "coordinateX", "address");
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
}
