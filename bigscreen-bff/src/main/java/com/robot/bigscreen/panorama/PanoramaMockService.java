package com.robot.bigscreen.panorama;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PanoramaMockService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> overview() {
        List<Map<String, Object>> devices = devices();
        List<Map<String, Object>> taskItems = taskItems();
        Map<String, Object> alarmGroups = alarmGroups();
        return Map.of(
                "serverTime", now(),
                "deviceStats", deviceStats(),
                "deviceTypeStats", deviceTypeStats(),
                "deviceGroups", deviceGroupsPayload(),
                "devices", devices,
                "tasks", taskItems,
                "alarms", alarmGroups,
                "map", mapConfig());
    }

    public Map<String, Object> deviceDetail(String deviceId) {
        return devices().stream()
                .filter(device -> deviceId.equals(device.get("robotId")))
                .findFirst()
                .map(this::toDeviceDetail)
                .orElseGet(() -> object(
                        "robotId", deviceId,
                        "name", "未知设备",
                        "status", "offline",
                        "type", "-",
                        "typeCode", "-",
                        "vendor", "-",
                        "model", "-",
                        "battery", 0,
                        "alarmStatus", "unknown",
                        "alarmText", "-",
                        "controlMode", "-",
                        "speed", null,
                        "mountedDeviceCount", 0,
                        "mountedDevices", List.of(),
                        "currentTask", null,
                        "actions", actions(false)));
    }

    public Map<String, Object> deviceGroups() {
        return object(
                "serverTime", now(),
                "deviceGroups", deviceGroupsPayload());
    }

    private Map<String, Object> deviceGroupsPayload() {
        Map<Boolean, List<Map<String, Object>>> grouped = devices().stream()
                .map(this::toDeviceGroupItem)
                .collect(Collectors.partitioningBy(item -> "online".equals(item.get("status")) || "fault".equals(item.get("status"))));
        List<Map<String, Object>> online = grouped.getOrDefault(true, List.of());
        List<Map<String, Object>> offline = grouped.getOrDefault(false, List.of());
        return Map.of(
                "total", online.size() + offline.size(),
                "online", deviceGroup("online", "在线", online),
                "offline", deviceGroup("offline", "离线", offline));
    }

    public Map<String, Object> tasks() {
        return Map.of(
                "serverTime", now(),
                "total", taskItems().size(),
                "items", taskItems());
    }

    public Map<String, Object> alarms() {
        return Map.of(
                "serverTime", now(),
                "alarms", alarmGroups());
    }

    private Map<String, Object> deviceStats() {
        return Map.of(
                "total", 22,
                "online", 18,
                "fault", 2,
                "offline", 2);
    }

    private List<Map<String, Object>> deviceTypeStats() {
        return List.of(
                deviceType("ROBOT_DOG", "机器狗", 8),
                deviceType("HUMANOID_ROBOT", "机器人", 6),
                deviceType("WHEELED_ROBOT", "轮式车", 8));
    }

    private List<Map<String, Object>> devices() {
        return List.of(
                device(
                        "robot-001",
                        "robot-media-client",
                        "松灵四轮机器人",
                        "轮式机器人",
                        "WHEELED_ROBOT",
                        "SONGLING",
                        "SCOUT",
                        "online",
                        100,
                        true,
                        "HIGH",
                        "AUTO",
                        0.4,
                        106.039428,
                        30.745330,
                        "A区仓库",
                        "wheeled_robot",
                        "告警中",
                        "alarm",
                        taskSummary("task-002", "A区-仓库复核", "paused", "10:00-11:30")),
                device(
                        "robot-002",
                        "robot-client-deep-001",
                        "云深处四足机器狗",
                        "四足机器狗",
                        "ROBOT_DOG",
                        "DEEPNROBOTICS",
                        "X30",
                        "offline",
                        78,
                        false,
                        null,
                        "MANUAL",
                        null,
                        106.03824884204943,
                        30.746587087515316,
                        "A区东侧通道",
                        "robot_dog",
                        "离线",
                        "offline",
                        null),
                device(
                        "robot-unitree-001",
                        "robot-client-unitree-001",
                        "宇树机器狗",
                        "四足机器狗",
                        "ROBOT_DOG",
                        "UNITREE",
                        "B2",
                        "offline",
                        92,
                        false,
                        null,
                        "MANUAL",
                        null,
                        106.0344109,
                        30.7469491,
                        "A区南侧仓库",
                        "robot_dog",
                        "离线",
                        "offline",
                        null));
    }

    private List<Map<String, Object>> taskItems() {
        return List.of(
                task(
                        "task-001",
                        "A区-夜间巡逻",
                        "running",
                        "执行中",
                        "2026-06-12 20:00:00",
                        "2026-06-12 22:00:00",
                        "A区主干道",
                        List.of(equipment("robot-001", "松灵四轮机器人", "WHEELED_ROBOT", "online"))),
                task(
                        "task-002",
                        "A区-仓库复核",
                        "paused",
                        "暂停中",
                        "2026-06-12 10:00:00",
                        "2026-06-12 11:30:00",
                        "A区仓库",
                        List.of(equipment("robot-001", "松灵四轮机器人", "WHEELED_ROBOT", "fault"))),
                task(
                        "task-003",
                        "南侧围栏巡查",
                        "pending",
                        "待执行",
                        "2026-06-12 14:00:00",
                        "2026-06-12 15:00:00",
                        "A区南侧仓库",
                        List.of(equipment("robot-unitree-001", "宇树机器狗", "ROBOT_DOG", "offline"))));
    }

    private Map<String, Object> alarmGroups() {
        List<Map<String, Object>> high = List.of(
                alarm("alarm-001", "发生火灾", "BUSINESS", "业务告警", "HIGH", "高风险", "2023-08-01 10:00:00", "A区仓库", "robot-001", "松灵四轮机器人", "task-002", "A区-仓库复核", "unhandled"),
                alarm("alarm-002", "设备故障", "DEVICE", "设备告警", "HIGH", "高风险", "2026-06-12 10:28:00", "A区仓库", "robot-001", "松灵四轮机器人", "task-002", "A区-仓库复核", "handling"));
        List<Map<String, Object>> medium = List.of(
                alarm("alarm-003", "电量偏低", "DEVICE", "设备告警", "MEDIUM", "中风险", "2026-06-12 10:15:00", "A区东侧通道", "robot-002", "云深处四足机器狗", null, null, "unhandled"));
        List<Map<String, Object>> low = List.of(
                alarm("alarm-004", "任务超时预警", "TASK", "任务告警", "LOW", "低风险", "2026-06-12 09:48:00", "A区主干道", "robot-001", "松灵四轮机器人", "task-001", "A区-夜间巡逻", "handled"));
        return Map.of(
                "total", 15,
                "high", alarmGroup("HIGH", "高风险", 5, high),
                "medium", alarmGroup("MEDIUM", "中风险", 5, medium),
                "low", alarmGroup("LOW", "低风险", 5, low));
    }

    private Map<String, Object> toDeviceDetail(Map<String, Object> device) {
        boolean online = "online".equals(device.get("status"));
        Object alarmLevel = device.get("alarmLevel");
        return object(
                "robotId", device.get("robotId"),
                "clientId", device.get("clientId"),
                "name", device.get("name"),
                "type", device.get("type"),
                "typeCode", device.get("typeCode"),
                "vendor", device.get("vendor"),
                "model", device.get("model"),
                "status", device.get("status"),
                "battery", device.get("battery"),
                "lastHeartbeatAt", device.get("lastHeartbeatAt"),
                "cameras", device.get("cameras"),
                "devices", device.get("devices"),
                "stateSeq", device.get("stateSeq"),
                "alarmStatus", alarmLevel == null ? "none" : alarmLevel,
                "alarmText", alarmLevel == null ? "-" : "存在未处理告警",
                "controlMode", device.get("controlMode"),
                "speed", device.get("speed"),
                "location", device.get("location"),
                "mountedDeviceCount", 3,
                "mountedDevices", mountedDevices(),
                "currentTask", device.get("task"),
                "actions", actions(online));
    }

    private Map<String, Object> toDeviceGroupItem(Map<String, Object> device) {
        Object alarmLevel = device.get("alarmLevel");
        @SuppressWarnings("unchecked")
        Map<String, Object> location = (Map<String, Object>) device.get("location");
        @SuppressWarnings("unchecked")
        Map<String, Object> task = (Map<String, Object>) device.get("task");
        return object(
                "robotId", device.get("robotId"),
                "clientId", device.get("clientId"),
                "name", device.get("name"),
                "type", device.get("type"),
                "typeCode", device.get("typeCode"),
                "vendor", device.get("vendor"),
                "model", device.get("model"),
                "status", device.get("status"),
                "battery", device.get("battery"),
                "fault", device.get("fault"),
                "alarmLevel", alarmLevel,
                "alarmText", alarmLevel == null ? "-" : "存在未处理告警",
                "locationText", location == null ? "-" : location.get("address"),
                "currentTaskId", task == null ? null : task.get("taskId"),
                "currentTaskName", task == null ? null : task.get("name"));
    }

    private Map<String, Object> deviceGroup(String status, String statusName, List<Map<String, Object>> items) {
        return Map.of(
                "status", status,
                "statusName", statusName,
                "count", items.size(),
                "items", items);
    }

    private Map<String, Object> mapConfig() {
        return Map.of(
                "center", Map.of("lng", 106.03655278081857, "lat", 30.7478613352993),
                "zoom", 17,
                "defaultLayer", "dark-vector",
                "updatedAt", now());
    }

    private Map<String, Object> deviceType(String type, String name, int count) {
        return Map.of("type", type, "name", name, "count", count);
    }

    private List<Map<String, Object>> cameras(String robotId, String typeCode) {
        if ("robot-001".equals(robotId)) {
            return List.of(
                    camera("camera01", "dual_gimbal", "云台-可见光"),
                    camera("camera02", "dual_gimbal", "云台-热成像"),
                    camera("camera03", "body", "本体相机"));
        }
        String cameraId = switch (typeCode) {
            case "ROBOT_DOG" -> "camera04";
            case "HUMANOID_ROBOT" -> "camera08";
            default -> "camera01";
        };
        return List.of(camera(cameraId, "dual_gimbal", "前向双光云台"));
    }

    private Map<String, Object> camera(String cameraId, String groupType, String name) {
        return Map.of(
                "cameraId", cameraId,
                "deviceId", cameraId,
                "groupType", groupType,
                "name", name,
                "quality", "sub");
    }

    private Map<String, Object> device(
            String robotId,
            String clientId,
            String name,
            String type,
            String typeCode,
            String vendor,
            String model,
            String status,
            int battery,
            boolean fault,
            String alarmLevel,
            String controlMode,
            Double speed,
            double lng,
            double lat,
            String address,
            String icon,
            String badgeText,
            String badgeStatus,
            Map<String, Object> task) {
        return object(
                "robotId", robotId,
                "clientId", clientId,
                "name", name,
                "type", type,
                "typeCode", typeCode,
                "vendor", vendor,
                "model", model,
                "status", status,
                "battery", battery,
                "lastHeartbeatAt", now(),
                "cameras", cameras(robotId, typeCode),
                "devices", mountedDevices(),
                "stateSeq", 1,
                "fault", fault,
                "alarmLevel", alarmLevel,
                "controlMode", controlMode,
                "mountedDeviceCount", mountedDevices().size(),
                "speed", speed,
                "location", object(
                        "lng", lng,
                        "lat", lat,
                        "altitude", null,
                        "address", address,
                        "updatedAt", now()),
                "mapDisplay", Map.of(
                        "icon", icon,
                        "label", name,
                        "badgeText", badgeText,
                        "badgeStatus", badgeStatus),
                "task", task);
    }

    private Map<String, Object> task(
            String taskId,
            String name,
            String status,
            String statusName,
            String startTime,
            String endTime,
            String currentLocation,
            List<Map<String, Object>> equipmentList) {
        return Map.of(
                "taskId", taskId,
                "name", name,
                "status", status,
                "statusName", statusName,
                "startTime", startTime,
                "endTime", endTime,
                "timeRange", startTime.substring(11, 16) + "-" + endTime.substring(11, 16),
                "currentLocation", currentLocation,
                "equipmentList", equipmentList);
    }

    private Map<String, Object> taskSummary(String taskId, String name, String status, String timeRange) {
        return Map.of(
                "taskId", taskId,
                "name", name,
                "status", status,
                "timeRange", timeRange);
    }

    private Map<String, Object> equipment(String robotId, String name, String type, String status) {
        return Map.of(
                "robotId", robotId,
                "name", name,
                "type", type,
                "status", status);
    }

    private Map<String, Object> alarmGroup(String level, String levelName, int count, List<Map<String, Object>> items) {
        return Map.of(
                "level", level,
                "levelName", levelName,
                "count", count,
                "items", items);
    }

    private Map<String, Object> alarm(
            String alarmId,
            String title,
            String category,
            String categoryName,
            String level,
            String levelName,
            String eventTime,
            String location,
            String robotId,
            String deviceName,
            String taskId,
            String taskName,
            String status) {
        return object(
                "alarmId", alarmId,
                "title", title,
                "category", category,
                "categoryName", categoryName,
                "level", level,
                "levelName", levelName,
                "eventTime", eventTime,
                "location", location,
                "robotId", robotId,
                "deviceName", deviceName,
                "taskId", taskId,
                "taskName", taskName,
                "status", status,
                "snapshotUrl", null);
    }

    private List<Map<String, Object>> mountedDevices() {
        return List.of(
                Map.of("deviceId", "camera01", "name", "前向双光云台", "type", "DUAL_GIMBAL", "status", "online"),
                Map.of("deviceId", "audio-control-001", "name", "客户端音频", "type", "CLIENT_AUDIO", "status", "online"),
                Map.of("deviceId", "ptz-001", "name", "云台控制", "type", "PTZ", "status", "online"));
    }

    private Map<String, Object> actions(boolean enabled) {
        return Map.of(
                "remoteControl", enabled,
                "slamMap", enabled,
                "returnHome", enabled,
                "returnChargingPile", enabled,
                "showPath", true,
                "showArea", true);
    }

    private String now() {
        return OffsetDateTime.now(CHINA_ZONE).format(DATE_TIME_FORMATTER);
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
