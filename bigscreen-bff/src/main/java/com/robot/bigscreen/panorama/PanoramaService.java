package com.robot.bigscreen.panorama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PanoramaService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PanoramaCenterClient centerClient;
    private final ObjectMapper objectMapper;

    public PanoramaService(PanoramaCenterClient centerClient, ObjectMapper objectMapper) {
        this.centerClient = centerClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> overview() {
        Map<String, Object> overview = object("serverTime", now());
        List<Map<String, Object>> devices = devices();
        overview.put("devices", devices);
        overview.put("deviceStats", deviceStats(devices));
        overview.put("deviceTypeStats", deviceTypeStats(devices));

        PanoramaTasks panoramaTasks = taskPayload();
        overview.put("patrolOverview", patrolOverview(panoramaTasks.instances()));
        overview.put("tasks", panoramaTasks.items());
        overview.put("taskOverview", taskOverview(panoramaTasks.items()));

        List<Map<String, Object>> maps = centerClient.enabledMaps();
        overview.put("map", maps);

        overview.put("alarms", alarmsPayload());
        return overview;
    }

    public Map<String, Object> deviceDetail(String deviceId) {
        return devices().stream()
                .filter(device -> Objects.equals(deviceId, string(device.get("robotId"))))
                .findFirst()
                .map(this::toDeviceDetail)
                .orElseGet(this::emptyDeviceDetail);
    }

    public Map<String, Object> tasks() {
        List<Map<String, Object>> tasks = taskPayload().items();
        return object(
                "serverTime", now(),
                "total", tasks.size(),
                "items", tasks);
    }

    public Map<String, Object> alarms() {
        return object(
                "serverTime", now(),
                "alarms", alarmsPayload());
    }

    public Map<String, Object> disposeAlarm(String alarmId, Map<String, Object> request) {
        if (alarmId == null || alarmId.isBlank()) {
            throw new IllegalArgumentException("alarmId is required");
        }
        AlarmDisposalStatus disposalStatus = AlarmDisposalStatus.from(string(request == null ? null : request.get("disposalStatus")));
        boolean success = centerClient.handleAlarm(alarmId, disposalStatus.code);
        return object(
                "success", success,
                "serverTime", now(),
                "alarmId", alarmId,
                "disposalStatus", disposalStatus.code,
                "disposalStatusName", disposalStatus.name,
                "status", success ? disposalStatus.alarmStatus : null,
                "message", success ? "告警处置状态已更新" : "告警处置状态更新失败");
    }

    private List<Map<String, Object>> devices() {
        List<Map<String, Object>> managementDevices = centerClient.devices();
        if (managementDevices.isEmpty()) {
            return List.of();
        }
        TaskInstanceResolver taskInstanceResolver = new TaskInstanceResolver(List.of());
        Map<String, Map<String, Object>> statusBySerial = statusBySerial(managementDevices);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> managementDevice : managementDevices) {
            String robotId = firstString(managementDevice, "serialNumber");
            Map<String, Object> realtimeStatus = statusBySerial.getOrDefault(robotId, Map.of());
            result.add(device(deviceSource(managementDevice), realtimeStatus, taskInstanceResolver));
        }
        return result;
    }

    private Map<String, Object> deviceSource(Map<String, Object> listDevice) {
        String id = firstString(listDevice, "id");
        if (id == null) {
            return listDevice;
        }
        return centerClient.device(id)
                .map(detail -> mergeDevice(listDevice, detail))
                .orElse(listDevice);
    }

    private Map<String, Object> mergeDevice(Map<String, Object> listDevice, Map<String, Object> detailDevice) {
        Map<String, Object> result = mutable(listDevice);
        detailDevice.forEach((key, value) -> {
            if (value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private Map<String, Map<String, Object>> statusBySerial(List<Map<String, Object>> managementDevices) {
        List<String> serialNumbers = managementDevices.stream()
                .map(device -> firstString(device, "serialNumber"))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        return centerClient.realtimeStatuses(serialNumbers).stream()
                .filter(status -> firstString(status, "serialNumber") != null)
                .collect(Collectors.toMap(
                        status -> firstString(status, "serialNumber"),
                        Function.identity(),
                        (left, right) -> right));
    }

    private Map<String, Object> device(
            Map<String, Object> source,
            Map<String, Object> realtimeStatus,
            TaskInstanceResolver taskInstanceResolver) {
        Map<String, Object> basic = map(path(realtimeStatus, "status", "basic"));
        Map<String, Object> motion = map(path(realtimeStatus, "status", "motion"));
        Map<String, Object> localization = map(path(realtimeStatus, "status", "localization"));
        Map<String, Object> energy = map(path(realtimeStatus, "status", "energy"));
        Map<String, Object> control = map(path(realtimeStatus, "status", "control"));
        Map<String, Object> task = map(path(realtimeStatus, "status", "task"));

        Object robotId = firstValue(source, "serialNumber");
        String status = onlineStatus(string(realtimeStatus.get("onlineStatus")));
        List<Map<String, Object>> mountedDevices = mountedDevices(source, status);
        Object alarmLevel = alarmLevel(basic);
        Object fault = fault(basic);
        String name = firstString(source, "deviceName", "name");

        return object(
                "robotId", robotId,
                "clientId", firstValue(source, "authMqttClientId", "clientId"),
                "name", name,
                "type", typeName(firstString(source, "deviceType", "typeCode", "type")),
                "typeCode", firstValue(source, "deviceType", "typeCode", "type"),
                "vendor", firstValue(source, "manufacturer", "vendor"),
                "model", firstValue(source, "model"),
                "status", status,
                "battery", number(energy.get("batteryPercent")),
                "lastHeartbeatAt", formatTime(firstString(realtimeStatus, "lastSeenAt", "receivedAt", "reportedAt")),
                "cameras", cameras(source, string(robotId)),
                "mountedDevices", mountedDevices,
                "stateSeq", number(realtimeStatus.get("stateSeq")),
                "fault", fault,
                "alarmLevel", alarmLevel,
                "controlMode", firstString(control, "controlMode"),
                "mountedDeviceCount", mountedDeviceCount(source, mountedDevices),
                "speed", number(motion.get("speed")),
                "location", location(localization, realtimeStatus),
                "mapDisplay", mapDisplay(name, status, alarmLevel),
                "task", deviceTasks(task, taskInstanceResolver));
    }

    private Object mountedDeviceCount(Map<String, Object> source, List<Map<String, Object>> mountedDevices) {
        return source.containsKey("components") ? mountedDevices.size() : null;
    }

    private List<Map<String, Object>> mountedDevices(Map<String, Object> source, String robotStatus) {
        List<Map<String, Object>> components = list(source.get("components"));
        if (components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                .map(component -> object(
                        "deviceId", firstValue(component, "code", "deviceId", "id"),
                        "name", firstString(component, "name", "componentName"),
                        "type", componentType(component),
                        "status", robotStatus))
                .toList();
    }

    private List<Map<String, Object>> cameras(Map<String, Object> source, String robotId) {
        List<Map<String, Object>> components = list(source.get("components"));
        List<Map<String, Object>> cameras = new ArrayList<>();
        components.stream()
                .filter(this::dualGimbalComponent)
                .forEach(component -> cameras.addAll(dualGimbalCameras(component)));
        if (robotId != null && !robotId.isBlank()) {
            cameras.add(camera(robotId, robotId, "body", "本体相机"));
        }
        return cameras;
    }

    private List<Map<String, Object>> dualGimbalCameras(Map<String, Object> component) {
        String deviceId = firstString(component, "deviceId", "code", "id");
        return List.of(
                camera(suffix(deviceId, "camera01"), deviceId, "dual_gimbal", "云台-可见光"),
                camera(suffix(deviceId, "camera02"), deviceId, "dual_gimbal", "云台-热成像"));
    }

    private Map<String, Object> camera(String cameraId, String deviceId, String groupType, String name) {
        return object(
                "cameraId", cameraId,
                "deviceId", deviceId,
                "groupType", groupType,
                "name", name,
                "quality", "sub");
    }

    private boolean dualGimbalComponent(Map<String, Object> component) {
        String name = firstString(component, "name", "componentName");
        return name != null && name.contains("双光云台");
    }

    private List<String> componentActions(Map<String, Object> component) {
        return list(component.get("capabilities")).stream()
                .flatMap(capability -> list(capability.get("actions")).stream())
                .map(action -> firstString(action, "code", "capabilityCode", "name"))
                .filter(Objects::nonNull)
                .map(action -> action.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String componentType(Map<String, Object> component) {
        return list(component.get("capabilities")).stream()
                .map(capability -> firstString(capability, "code", "capabilityCode"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> location(
            Map<String, Object> localization,
            Map<String, Object> realtimeStatus) {
        return object(
                "lng", number(firstValue(localization, "lng", "longitude")),
                "lat", number(firstValue(localization, "lat", "latitude")),
                "altitude", number(firstValue(localization, "altitude")),
                "x", number(localization.get("coordinateX")),
                "y", number(localization.get("coordinateY")),
                "z", number(localization.get("coordinateZ")),
                "address", firstString(localization, "address"),
                "updatedAt", formatTime(firstString(realtimeStatus, "reportedAt", "receivedAt", "lastSeenAt")));
    }

    private Map<String, Object> emptyLocation() {
        return object(
                "lng", null,
                "lat", null,
                "altitude", null,
                "x", null,
                "y", null,
                "z", null,
                "address", null,
                "updatedAt", null);
    }

    private List<Map<String, Object>> deviceTasks(Map<String, Object> realtimeTask, TaskInstanceResolver taskInstanceResolver) {
        Object taskId = realtimeTask.get("taskInstanceId");
        if (taskId == null) {
            return List.of();
        }
        Map<String, Object> instance = taskInstanceResolver.instance(taskId);
        String startTime = formatTime(firstString(instance, "startedAt", "lastStartedAt", "startTime"));
        String endTime = formatTime(firstString(instance, "completedAt", "lastCompletedAt", "endTime"));
        return List.of(object(
                "taskId", taskId,
                "name", value(firstString(realtimeTask, "taskName", "workflowName"), firstString(instance, "workflowName", "planName", "name")),
                "status", statusCode(value(firstString(realtimeTask, "taskStatus", "status"), firstString(instance, "status"))),
                "timeRange", timeRange(startTime, endTime, null)));
    }

    private PanoramaTasks taskPayload() {
        List<Map<String, Object>> taskPlans = centerClient.taskWorkflowPlans();
        List<Map<String, Object>> taskInstances = centerClient.taskWorkflowInstances();
        if (taskPlans.isEmpty()) {
            return new PanoramaTasks(List.of(), taskInstances);
        }
        TaskInstanceResolver taskInstanceResolver = new TaskInstanceResolver(taskInstances);
        TaskRouteResolver routeResolver = new TaskRouteResolver(taskPlans);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < taskPlans.size(); index++) {
            Map<String, Object> sourceTask = taskPlans.get(index);
            result.add(taskItem(sourceTask, routeResolver.resolve(sourceTask, index), taskInstanceResolver));
        }
        return new PanoramaTasks(result, taskInstances);
    }

    private Map<String, Object> taskItem(
            Map<String, Object> source,
            TaskRouteData routeData,
            TaskInstanceResolver taskInstanceResolver) {
        Object workflowInstanceId = planWorkflowInstanceId(source);
        Map<String, Object> instance = taskInstanceResolver.instance(workflowInstanceId);
        Map<String, Object> replay = taskInstanceResolver.replay(workflowInstanceId);
        List<Map<String, Object>> deviceTaskInstances = taskInstanceResolver.deviceTaskInstances(workflowInstanceId);
        String rawStatus = firstString(
                object("activeWorkflowInstanceStatus", firstString(source, "activeWorkflowInstanceStatus"),
                        "instanceStatus", firstString(instance, "status"),
                        "executionStatus", firstString(source, "executionStatus"),
                        "lastResultStatus", firstString(source, "lastResultStatus"),
                        "status", firstString(source, "status")),
                "activeWorkflowInstanceStatus",
                "instanceStatus",
                "executionStatus",
                "lastResultStatus",
                "status");
        String startTime = formatTime(value(
                firstString(instance, "startedAt"),
                firstString(source, "startedAt", "lastStartedAt", "startTime")));
        String endTime = formatTime(value(
                firstString(instance, "completedAt"),
                firstString(source, "completedAt", "lastCompletedAt", "endTime")));
        return object(
                "taskId", firstValue(source, "id", "taskId"),
                "name", firstString(source, "planName", "workflowName", "name"),
                "status", statusCode(rawStatus),
                "statusName", statusName(rawStatus),
                "startTime", startTime,
                "endTime", endTime,
                "timeRange", timeRange(startTime, endTime, null),
                "currentLocation", currentLocation(source, replay),
                "equipmentList", equipmentList(source, instance, replay, deviceTaskInstances),
                "mapId", routeData.mapId(),
                "mapPoints", routeData.mapPoints(),
                "pathPoints", routeData.pathPoints());
    }

    private Object planWorkflowInstanceId(Map<String, Object> source) {
        return firstValue(source, "activeWorkflowInstanceId", "lastWorkflowInstanceId", "workflowInstanceId");
    }

    private String currentLocation(Map<String, Object> source, Map<String, Object> replay) {
        String currentLocation = firstString(source, "currentLocation");
        if (currentLocation != null) {
            return currentLocation;
        }
        return firstString(latestTrackSample(replay), "pointName");
    }

    private Map<String, Object> latestTrackSample(Map<String, Object> replay) {
        Map<String, Object> latest = Map.of();
        for (Map<String, Object> trackGroup : list(replay.get("trackGroups"))) {
            for (Map<String, Object> sample : list(trackGroup.get("samples"))) {
                latest = sample;
            }
        }
        return latest;
    }

    private List<Map<String, Object>> equipmentList(
            Map<String, Object> source,
            Map<String, Object> instance,
            Map<String, Object> replay,
            List<Map<String, Object>> deviceTaskInstances) {
        if (!deviceTaskInstances.isEmpty()) {
            return deviceTaskInstances.stream()
                    .map(task -> object(
                            "robotId", firstValue(task, "serialNumber", "deviceId", "id"),
                            "name", firstString(task, "deviceName", "name"),
                            "type", typeName(firstString(task, "deviceType", "type")),
                            "status", statusCode(firstString(task, "status"))))
                    .toList();
        }
        List<Map<String, Object>> summaries = list(value(instance.get("deviceSummaries"), replay.get("deviceSummaries")));
        if (summaries.isEmpty()) {
            summaries = list(source.get("deviceSummaries"));
        }
        if (!summaries.isEmpty()) {
            return summaries.stream()
                    .map(summary -> object(
                            "robotId", firstValue(summary, "serialNumber", "deviceId", "id"),
                            "name", firstString(summary, "deviceName", "name"),
                            "type", typeName(firstString(summary, "deviceType", "type")),
                            "status", statusCode(firstString(summary, "status"))))
                    .toList();
        }
        List<Map<String, Object>> roleBindings = list(source.get("roleBindings"));
        if (roleBindings.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> roleBinding : roleBindings) {
            for (Object deviceId : scalarList(roleBinding.get("deviceIds"))) {
                result.add(object(
                        "robotId", deviceId,
                        "name", null,
                        "type", null,
                        "status", null));
            }
        }
        return result;
    }

    private Map<String, Object> alarmsPayload() {
        List<Map<String, Object>> alarms = centerClient.alarms();
        TaskInstanceResolver taskInstanceResolver = new TaskInstanceResolver(List.of());
        List<Map<String, Object>> items = alarms.stream()
                .map(alarm -> alarmItem(alarm, taskInstanceResolver))
                .toList();
        List<Map<String, Object>> high = filterAlarms(items, "HIGH");
        List<Map<String, Object>> medium = filterAlarms(items, "MEDIUM");
        List<Map<String, Object>> low = filterAlarms(items, "LOW");
        return object(
                "total", items.size(),
                "summary", alarmSummary(items),
                "high", alarmGroup(high),
                "medium", alarmGroup(medium),
                "low", alarmGroup(low));
    }

    private Map<String, Object> alarmItem(Map<String, Object> source, TaskInstanceResolver taskInstanceResolver) {
        Object taskId = firstValue(source, "taskInstanceId", "taskId");
        Map<String, Object> taskInstance = taskInstanceResolver.instance(taskId);
        return object(
                "alarmId", firstValue(source, "id", "alarmId", "alarmCode"),
                "title", firstString(source, "title", "alarmName"),
                "categoryName", categoryName(firstString(source, "alarmType", "category")),
                "level", levelCode(firstString(source, "severity", "level")),
                "levelName", levelName(firstString(source, "severity", "level")),
                "eventTime", formatTime(firstString(source, "occurredAt", "eventTime", "createdAt")),
                "location", alarmLocation(source),
                "robotId", firstValue(source, "serialNumber", "robotId", "deviceCode"),
                "deviceName", firstString(source, "deviceName"),
                "taskId", taskId,
                "taskName", value(firstString(source, "taskName", "workflowName"), firstString(taskInstance, "workflowName", "planName", "name")),
                "status", statusCode(firstString(source, "status")),
                "snapshotUrl", snapshotUrl(source));
    }

    private Map<String, Object> alarmLocation(Map<String, Object> source) {
        Map<String, Object> location = map(source.get("location"));
        if (location.isEmpty()) {
            location = map(rawPayload(source).get("location"));
        }
        Object lng = firstValue(location, "lng", "longitude");
        Object lat = firstValue(location, "lat", "latitude");
        Object altitude = firstValue(location, "altitude");
        Object x = firstValue(location, "x", "coordinateX");
        Object y = firstValue(location, "y", "coordinateY");
        Object z = firstValue(location, "z", "coordinateZ");
        String address = firstString(location, "address");
        String updatedAt = formatTime(firstString(location, "updatedAt", "reportedAt", "receivedAt"));
        if (lng == null && lat == null && altitude == null && x == null && y == null && z == null && address == null && updatedAt == null) {
            return null;
        }
        return object(
                "lng", number(lng),
                "lat", number(lat),
                "altitude", number(altitude),
                "x", number(x),
                "y", number(y),
                "z", number(z),
                "address", address,
                "updatedAt", updatedAt);
    }

    private Map<String, Object> snapshotUrl(Map<String, Object> source) {
        Map<String, Object> snapshotUrl = map(source.get("snapshotUrl"));
        if (snapshotUrl.isEmpty()) {
            snapshotUrl = map(rawPayload(source).get("snapshotUrl"));
        }
        String visible = firstString(snapshotUrl, "visible");
        String thermal = firstString(snapshotUrl, "thermal");
        String front = firstString(snapshotUrl, "front");
        if (visible == null) {
            visible = alarmImageUrl(source);
        }
        if (visible == null && thermal == null && front == null) {
            return null;
        }
        return object("visible", visible, "thermal", thermal, "front", front);
    }

    private String alarmImageUrl(Map<String, Object> source) {
        String imageUrl = firstString(source, "imageUrl");
        if (imageUrl != null) {
            return imageUrl;
        }
        String imageFileId = firstString(source, "imageFileId");
        return imageFileId == null ? null : "/api/control/files/" + imageFileId + "/content";
    }

    private Map<String, Object> alarmSummary(List<Map<String, Object>> alarms) {
        long handled = alarms.stream().filter(alarm -> handled(string(alarm.get("status")))).count();
        int total = alarms.size();
        Integer handleRate = total == 0 ? null : (int) Math.round(handled * 100.0 / total);
        return object(
                "totalToday", total,
                "handled", handled,
                "unhandled", total - handled,
                "handleRate", handleRate,
                "handleRateText", handleRate == null ? null : handleRate + "%");
    }

    private Map<String, Object> taskOverview(List<Map<String, Object>> tasks) {
        long running = tasks.stream().filter(task -> "running".equals(task.get("status"))).count();
        long pending = tasks.stream().filter(task -> "pending".equals(task.get("status"))).count();
        long completed = tasks.stream().filter(task -> "completed".equals(task.get("status")) || "handled".equals(task.get("status"))).count();
        int total = tasks.size();
        Integer completedRate = total == 0 ? null : (int) Math.round(completed * 100.0 / total);
        return object(
                "totalToday", total,
                "completedRate", completedRate,
                "completedRateText", completedRate == null ? null : completedRate + "%",
                "running", running,
                "pending", pending);
    }

    private Map<String, Object> deviceStats(List<Map<String, Object>> devices) {
        long online = devices.stream().filter(device -> "online".equals(device.get("status"))).count();
        long fault = devices.stream().filter(device -> booleanValue(device.get("fault"))).count();
        long offline = devices.stream().filter(device -> "offline".equals(device.get("status"))).count();
        return object(
                "total", devices.size(),
                "online", online,
                "fault", fault,
                "offline", offline);
    }

    private List<Map<String, Object>> deviceTypeStats(List<Map<String, Object>> devices) {
        Map<String, List<Map<String, Object>>> grouped = devices.stream()
                .collect(Collectors.groupingBy(device -> value(string(device.get("typeCode")), "-"), LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Map<String, Object>> items = entry.getValue();
                    String name = items.stream().map(item -> string(item.get("type"))).filter(Objects::nonNull).findFirst().orElse(entry.getKey());
                    long fault = items.stream().filter(item -> booleanValue(item.get("fault"))).count();
                    long offline = items.stream().filter(item -> "offline".equals(item.get("status"))).count();
                    return object("type", entry.getKey(), "name", name, "count", items.size(), "fault", fault, "offline", offline);
                })
                .toList();
    }

    private Map<String, Object> toDeviceDetail(Map<String, Object> device) {
        Object alarmLevel = device.get("alarmLevel");
        boolean online = "online".equals(device.get("status"));
        boolean statusKnown = device.get("status") != null;
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
                "stateSeq", device.get("stateSeq"),
                "alarmStatus", alarmLevel,
                "alarmText", alarmLevel == null ? null : "存在未处理告警",
                "controlMode", device.get("controlMode"),
                "speed", device.get("speed"),
                "location", device.get("location"),
                "mountedDeviceCount", device.get("mountedDeviceCount"),
                "mountedDevices", device.get("mountedDevices"),
                "currentTask", taskArray(device.get("task")),
                "actions", statusKnown ? actions(online) : emptyActions());
    }

    private Map<String, Object> emptyDeviceDetail() {
        return object(
                "robotId", null,
                "clientId", null,
                "name", null,
                "type", null,
                "typeCode", null,
                "vendor", null,
                "model", null,
                "status", null,
                "battery", null,
                "lastHeartbeatAt", null,
                "cameras", List.of(),
                "stateSeq", null,
                "alarmStatus", null,
                "alarmText", null,
                "controlMode", null,
                "speed", null,
                "location", emptyLocation(),
                "mountedDeviceCount", null,
                "mountedDevices", List.of(),
                "currentTask", List.of(),
                "actions", emptyActions());
    }

    private Map<String, Object> patrolOverview(List<Map<String, Object>> taskInstances) {
        long durationSeconds = taskInstances.stream()
                .filter(this::todayTaskInstance)
                .map(this::durationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        Double durationToday = durationSeconds == 0 ? null : oneDecimal(durationSeconds / 3600.0);
        return object(
                "durationToday", durationToday,
                "durationUnit", durationToday == null ? null : "小时",
                "mileageToday", null,
                "mileageUnit", null);
    }

    private boolean todayTaskInstance(Map<String, Object> taskInstance) {
        LocalDateTime startedAt = parseTime(firstString(taskInstance, "startedAt", "lastStartedAt"));
        LocalDateTime completedAt = parseTime(firstString(taskInstance, "completedAt", "lastCompletedAt"));
        LocalDate today = LocalDate.now(CHINA_ZONE);
        return (startedAt != null && startedAt.toLocalDate().equals(today))
                || (completedAt != null && completedAt.toLocalDate().equals(today));
    }

    private Long durationSeconds(Map<String, Object> taskInstance) {
        Number durationSeconds = number(taskInstance.get("durationSeconds"));
        if (durationSeconds != null) {
            return Math.round(durationSeconds.doubleValue());
        }
        LocalDateTime startedAt = parseTime(firstString(taskInstance, "startedAt", "lastStartedAt"));
        LocalDateTime completedAt = parseTime(firstString(taskInstance, "completedAt", "lastCompletedAt"));
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toSeconds();
    }

    private Double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Map<String, Object> actions(boolean enabled) {
        return object(
                "remoteControl", enabled,
                "slamMap", enabled,
                "returnHome", enabled,
                "returnChargingPile", enabled,
                "showPath", true,
                "showArea", true);
    }

    private Map<String, Object> emptyActions() {
        return object(
                "remoteControl", null,
                "slamMap", null,
                "returnHome", null,
                "returnChargingPile", null,
                "showPath", null,
                "showArea", null);
    }

    private Map<String, Object> mapDisplay(String name, String status, Object alarmLevel) {
        return object(
                "icon", null,
                "label", name,
                "badgeText", alarmLevel == null ? statusName(status) : "告警中",
                "badgeStatus", alarmLevel == null ? status : "alarm");
    }

    private Object alarmLevel(Map<String, Object> basic) {
        String alarmStatus = string(basic.get("alarmStatus"));
        if (alarmStatus == null || alarmStatus.isBlank() || "NONE".equalsIgnoreCase(alarmStatus) || "NORMAL".equalsIgnoreCase(alarmStatus)) {
            return null;
        }
        return levelCode(alarmStatus);
    }

    private Map<String, Object> alarmGroup(List<Map<String, Object>> items) {
        return object("items", items);
    }

    private List<Map<String, Object>> filterAlarms(List<Map<String, Object>> alarms, String level) {
        return alarms.stream().filter(alarm -> level.equals(alarm.get("level"))).toList();
    }

    private final class TaskInstanceResolver {

        private final Map<String, Map<String, Object>> instancesById = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> replaysById = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> deviceTasksByWorkflowInstanceId = new LinkedHashMap<>();

        private TaskInstanceResolver(List<Map<String, Object>> taskInstances) {
            for (Map<String, Object> taskInstance : taskInstances) {
                String id = firstString(taskInstance, "id", "workflowInstanceId");
                if (id != null) {
                    instancesById.putIfAbsent(id, taskInstance);
                }
            }
        }

        private Map<String, Object> instance(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return Map.of();
            }
            return instancesById.computeIfAbsent(id, this::loadInstance);
        }

        private Map<String, Object> replay(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return Map.of();
            }
            return replaysById.computeIfAbsent(id,
                    value -> centerClient.taskWorkflowReplay(value).orElse(Map.of()));
        }

        private List<Map<String, Object>> deviceTaskInstances(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return List.of();
            }
            return deviceTasksByWorkflowInstanceId.computeIfAbsent(id, centerClient::deviceTaskInstances);
        }

        private Map<String, Object> loadInstance(String workflowInstanceId) {
            return centerClient.taskWorkflowInstance(workflowInstanceId)
                    .map(this::unwrapInstance)
                    .orElse(Map.of());
        }

        private Map<String, Object> unwrapInstance(Map<String, Object> source) {
            Map<String, Object> instance = map(source.get("instance"));
            return instance.isEmpty() ? source : instance;
        }

        private String key(Object value) {
            String text = string(value);
            return text == null || text.isBlank() ? null : text;
        }
    }

    private final class TaskRouteResolver {

        private final List<Map<String, Object>> plans;
        private final Map<String, Map<String, Object>> plansById;
        private final Map<String, Map<String, Object>> plansByName;
        private final Map<String, TaskRouteData> routesByDefinitionId = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> mapPointsByMapId = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> pathPointsByPathId = new LinkedHashMap<>();

        private TaskRouteResolver(List<Map<String, Object>> plans) {
            this.plans = plans == null ? List.of() : plans;
            this.plansById = indexPlans("id", "planId", "workflowPlanId", "taskWorkflowPlanId", "code");
            this.plansByName = indexPlans("planName", "workflowName", "name");
        }

        private TaskRouteData resolve(Map<String, Object> source, int index) {
            String workflowDefinitionId = value(
                    firstString(source, "workflowDefinitionId", "definitionId"),
                    firstString(map(source.get("workflowDefinition")), "id", "workflowDefinitionId"));
            if (workflowDefinitionId == null) {
                workflowDefinitionId = firstString(plan(source, index), "workflowDefinitionId", "definitionId");
            }
            if (workflowDefinitionId == null || workflowDefinitionId.isBlank()) {
                return TaskRouteData.empty();
            }
            return routesByDefinitionId.computeIfAbsent(workflowDefinitionId, this::routeData);
        }

        private Map<String, Object> plan(Map<String, Object> source, int index) {
            String planId = firstString(source, "workflowPlanId", "planId", "taskPlanId", "taskWorkflowPlanId");
            Map<String, Object> plan = plansById.get(key(planId));
            if (plan != null) {
                return plan;
            }
            String planName = firstString(source, "planName", "workflowName", "name");
            plan = plansByName.get(key(planName));
            if (plan != null) {
                return plan;
            }
            if (plans.size() == 1) {
                return plans.get(0);
            }
            if (index >= 0 && index < plans.size()) {
                return plans.get(index);
            }
            return Map.of();
        }

        private TaskRouteData routeData(String workflowDefinitionId) {
            Map<String, Object> definition = centerClient.taskWorkflowDefinition(workflowDefinitionId).orElse(Map.of());
            Object mapId = firstValue(definition, "mapId", "mapID");
            Object pathId = firstValue(definition, "pathId", "routeId");
            List<Map<String, Object>> mapPoints = mapId == null
                    ? List.of()
                    : mapPointsByMapId.computeIfAbsent(string(mapId), centerClient::mapPoints);
            List<Map<String, Object>> pathPointRefs = pathId == null
                    ? List.of()
                    : pathPointsByPathId.computeIfAbsent(string(pathId), centerClient::pathPoints);
            return new TaskRouteData(mapId, mapPoints, resolvePathPoints(mapPoints, pathPointRefs));
        }

        private Map<String, Map<String, Object>> indexPlans(String... keys) {
            Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
            for (Map<String, Object> plan : plans) {
                String value = firstString(plan, keys);
                if (value != null && !value.isBlank()) {
                    indexed.putIfAbsent(key(value), plan);
                }
            }
            return indexed;
        }

        private String key(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private String onlineStatus(String source) {
        if (source == null) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "ONLINE", "ON_LINE" -> "online";
            case "FAULT" -> "fault";
            case "OFFLINE", "OFF_LINE" -> "offline";
            default -> source.toLowerCase(Locale.ROOT);
        };
    }

    private Object fault(Map<String, Object> basic) {
        String healthStatus = string(basic.get("healthStatus"));
        if (healthStatus == null || healthStatus.isBlank()) {
            return null;
        }
        return !"NORMAL".equalsIgnoreCase(healthStatus);
    }

    private String statusCode(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "RUNNING", "EXECUTING" -> "running";
            case "PENDING", "WAITING", "READY" -> "pending";
            case "PAUSED", "SUSPENDED" -> "paused";
            case "COMPLETED", "SUCCESS", "FINISHED" -> "completed";
            case "HANDLED", "ACKNOWLEDGED" -> "handled";
            case "FALSE_ALARM" -> "false_alarm";
            case "FAILED", "ERROR" -> "failed";
            case "UNHANDLED", "NEW" -> "unhandled";
            case "HANDLING" -> "handling";
            default -> source.toLowerCase(Locale.ROOT);
        };
    }

    private String statusName(String source) {
        String status = statusCode(source);
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "online" -> "在线";
            case "offline" -> "离线";
            case "fault" -> "故障";
            case "running" -> "执行中";
            case "pending" -> "待执行";
            case "paused" -> "暂停中";
            case "completed" -> "已完成";
            case "handled" -> "已处理";
            case "false_alarm" -> "误报";
            case "failed" -> "失败";
            case "unhandled" -> "未处理";
            case "handling" -> "处理中";
            default -> source;
        };
    }

    private String levelCode(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "HIGH", "CRITICAL", "SEVERE" -> "HIGH";
            case "MEDIUM", "MIDDLE", "WARN", "WARNING" -> "MEDIUM";
            case "LOW", "INFO", "NORMAL" -> "LOW";
            default -> source.toUpperCase(Locale.ROOT);
        };
    }

    private String levelName(String source) {
        String level = levelCode(source);
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            default -> source;
        };
    }

    private String categoryName(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "BUSINESS" -> "业务告警";
            case "DEVICE" -> "设备告警";
            case "TASK" -> "任务告警";
            default -> source;
        };
    }

    private String typeName(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return null;
        }
        return switch (typeCode.toUpperCase(Locale.ROOT)) {
            case "ROBOT_DOG" -> "机器狗";
            case "HUMANOID_ROBOT" -> "机器人";
            case "WHEELED_ROBOT" -> "轮式车";
            case "UAV_ROBOT" -> "无人机";
            default -> typeCode;
        };
    }

    private boolean handled(String status) {
        return "handled".equals(status) || "false_alarm".equals(status) || "acknowledged".equals(status);
    }

    private String timeRange(String startTime, String endTime, String fallback) {
        if (startTime == null || endTime == null || startTime.length() < 16 || endTime.length() < 16) {
            return fallback;
        }
        return startTime.substring(11, 16) + "-" + endTime.substring(11, 16);
    }

    private String now() {
        return OffsetDateTime.now(CHINA_ZONE).format(DATE_TIME_FORMATTER);
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LocalDateTime time = parseTime(raw);
        return time == null ? raw : time.format(DATE_TIME_FORMATTER);
    }

    private LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(CHINA_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local datetime below.
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            // Try date-only below.
        }
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Object path(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(key);
        }
        return current;
    }

    private Map<String, Object> rawPayload(Map<String, Object> source) {
        Object rawPayload = source.get("rawPayload");
        if (rawPayload instanceof Map<?, ?>) {
            return map(rawPayload);
        }
        String text = string(rawPayload);
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private List<Object> scalarList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !(item instanceof String text) || !text.isBlank())
                    .map(item -> (Object) item)
                    .toList();
        }
        if (value instanceof String text && text.isBlank()) {
            return List.of();
        }
        return value == null ? List.of() : List.of(value);
    }

    private List<Map<String, Object>> taskArray(Object value) {
        List<Map<String, Object>> tasks = list(value);
        if (!tasks.isEmpty()) {
            return tasks;
        }
        Map<String, Object> task = map(value);
        return task.isEmpty() ? List.of() : List.of(task);
    }

    private Map<String, Object> mutable(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length - 1; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = string(map.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> resolvePathPoints(
            List<Map<String, Object>> mapPoints,
            List<Map<String, Object>> pathPointRefs) {
        if (mapPoints.isEmpty() || pathPointRefs.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> mapPointsById = mapPoints.stream()
                .filter(point -> firstValue(point, "id") != null)
                .collect(Collectors.toMap(
                        point -> string(firstValue(point, "id")),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> pathPointRef : pathPointRefs) {
            Map<String, Object> mapPoint = mapPointsById.get(string(firstValue(pathPointRef, "mapPointId")));
            if (mapPoint != null) {
                result.add(mapPoint);
            }
        }
        return result;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String suffix(String value, String suffix) {
        return value == null || value.isBlank() ? null : value + suffix;
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private <T> T value(T value, T fallback) {
        if (value instanceof String text && text.isBlank()) {
            return fallback;
        }
        return value == null ? fallback : value;
    }

    private record TaskRouteData(
            Object mapId,
            List<Map<String, Object>> mapPoints,
            List<Map<String, Object>> pathPoints) {

        private static TaskRouteData empty() {
            return new TaskRouteData(null, List.of(), List.of());
        }
    }

    private record PanoramaTasks(
            List<Map<String, Object>> items,
            List<Map<String, Object>> instances) {
    }

    private enum AlarmDisposalStatus {
        IMMEDIATE_DISPOSAL("IMMEDIATE_DISPOSAL", "立即处置", "handled"),
        FALSE_ALARM("FALSE_ALARM", "误报", "false_alarm");

        private final String code;
        private final String name;
        private final String alarmStatus;

        AlarmDisposalStatus(String code, String name, String alarmStatus) {
            this.code = code;
            this.name = name;
            this.alarmStatus = alarmStatus;
        }

        private static AlarmDisposalStatus from(String rawStatus) {
            if (rawStatus == null || rawStatus.isBlank()) {
                throw new IllegalArgumentException("disposalStatus is required");
            }
            String normalized = rawStatus.trim();
            for (AlarmDisposalStatus status : values()) {
                if (status.code.equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("disposalStatus must be one of IMMEDIATE_DISPOSAL, FALSE_ALARM");
        }
    }
}
