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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PanoramaService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ExecutorService IO_EXECUTOR = new ThreadPoolExecutor(
            8,
            64,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "panorama-io");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final PanoramaCenterClient centerClient;
    private final ObjectMapper objectMapper;

    public PanoramaService(PanoramaCenterClient centerClient, ObjectMapper objectMapper) {
        this.centerClient = centerClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> overview() {
        OverviewRequestCache cache = new OverviewRequestCache();
        Map<String, Object> overview = object("serverTime", now());
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(() -> devices(cache));
        CompletableFuture<PanoramaTasks> tasksFuture = async(() -> taskPayload(cache));
        CompletableFuture<List<Map<String, Object>>> mapsFuture = async(centerClient::enabledMaps);
        CompletableFuture<Map<String, Object>> alarmsFuture = async(this::alarmsPayload);
        CompletableFuture<Map<String, Object>> mileageFuture = async(this::todayMileageSummary);

        List<Map<String, Object>> maps = join(mapsFuture, List.of());
        prefetchMapResources(maps, cache);
        List<Map<String, Object>> rawDevices = join(devicesFuture, List.of());
        PanoramaTasks panoramaTasks = join(tasksFuture, new PanoramaTasks(List.of(), List.of()));
        List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(panoramaTasks.items(), rawDevices);
        List<Map<String, Object>> devices = withTaskAssociations(rawDevices, tasks);
        overview.put("devices", overviewDevices(devices));
        overview.put("deviceStats", deviceStats(devices));
        overview.put("deviceTypeStats", deviceTypeStats(devices));

        overview.put("patrolOverview", patrolOverview(
                panoramaTasks.instances(), join(mileageFuture, Map.of())));
        overview.put("tasks", overviewTasks(tasks));
        overview.put("taskOverview", overviewTaskOverview(tasks));

        overview.put("map", mapsWithPointsAndDevices(maps, devices, cache));

        overview.put("alarms", overviewAlarms(join(alarmsFuture, emptyAlarmsPayload())));
        return overview;
    }

    public Map<String, Object> statsSnapshot() {
        OverviewRequestCache cache = new OverviewRequestCache();
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(() -> devices(cache));
        CompletableFuture<PanoramaTasks> tasksFuture = async(() -> taskPayload(cache));
        CompletableFuture<Map<String, Object>> alarmsFuture = async(this::alarmsPayload);
        CompletableFuture<Map<String, Object>> mileageFuture = async(this::todayMileageSummary);

        List<Map<String, Object>> devices = join(devicesFuture, List.of());
        PanoramaTasks panoramaTasks = join(tasksFuture, new PanoramaTasks(List.of(), List.of()));
        List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(panoramaTasks.items(), devices);
        Map<String, Object> alarms = join(alarmsFuture, emptyAlarmsPayload());
        return object(
                "deviceStats", deviceStats(devices),
                "deviceTypeStats", deviceTypeStats(devices),
                "patrolOverview", patrolOverview(panoramaTasks.instances(), join(mileageFuture, Map.of())),
                "taskOverview", taskOverview(tasks),
                "alarmStats", alarmStats(alarms),
                "alarmSummary", alarms.get("summary"));
    }

    private List<Map<String, Object>> mapsWithPointsAndDevices(
            List<Map<String, Object>> maps,
            List<Map<String, Object>> devices,
            OverviewRequestCache cache) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<Map<String, Object>>> futures = maps.stream()
                .map(source -> async(() -> mapWithPointsAndDevices(source, devices, cache)))
                .toList();
        return futures.stream()
                .map(future -> join(future, null))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Map<String, Object>> prefetchMapResources(
            List<Map<String, Object>> maps,
            OverviewRequestCache cache) {
        if (maps == null) {
            return List.of();
        }
        for (Map<String, Object> map : maps) {
            String mapId = firstString(map, "id", "mapId");
            if (mapId != null) {
                cache.mapPointsFuture(mapId);
            }
        }
        cache.allFixedCamerasFuture();
        return maps;
    }

    private Map<String, Object> mapWithPointsAndDevices(
            Map<String, Object> source,
            List<Map<String, Object>> devices,
            OverviewRequestCache cache) {
        Map<String, Object> map = overviewMap(source);
        String mapId = firstString(source, "id", "mapId");
        CompletableFuture<List<Map<String, Object>>> pointsFuture = mapId == null
                ? CompletableFuture.completedFuture(List.of())
                : cache.mapPointsFuture(mapId);
        CompletableFuture<List<Map<String, Object>>> fixedCamerasFuture = mapId == null
                ? CompletableFuture.completedFuture(List.of())
                : cache.fixedCamerasFuture(mapId);
        List<Map<String, Object>> points = join(pointsFuture, List.of());
        List<Map<String, Object>> fixedCameras = join(fixedCamerasFuture, List.of());
        map.put("points", overviewPoints(points));
        map.remove("devices");
        map.put("deviceIds", deviceIdsForMap(mapId, devices));
        map.put("fixedCamares", fixedCameras);
        return map;
    }

    private List<Map<String, Object>> overviewDevices(List<Map<String, Object>> devices) {
        return devices.stream().map(this::overviewDevice).toList();
    }

    private Map<String, Object> overviewDevice(Map<String, Object> source) {
        Map<String, Object> device = mutable(source);
        device.remove("clientId");
        device.remove("vendor");
        device.remove("lastHeartbeatAt");
        device.remove("mountedDevices");
        device.remove("mapDisplay");
        Map<String, Object> location = mutable(map(device.get("location")));
        location.remove("altitude");
        location.remove("updatedAt");
        device.put("location", location);
        return device;
    }

    private List<Map<String, Object>> overviewTasks(List<Map<String, Object>> tasks) {
        return tasks.stream()
                .map(source -> {
                    Map<String, Object> task = mutable(source);
                    task.remove("mapPoints");
                    task.put("pathPoints", overviewPoints(list(task.get("pathPoints"))));
                    return task;
                })
                .toList();
    }

    private Map<String, Object> overviewMap(Map<String, Object> source) {
        Map<String, Object> map = mutable(source);
        map.remove("mapCode");
        map.remove("mapType");
        map.remove("regionId");
        map.remove("fileName");
        map.remove("previewImageUrl");
        map.remove("enabled");
        map.remove("remark");
        return map;
    }

    private List<Map<String, Object>> overviewPoints(List<Map<String, Object>> points) {
        return points.stream()
                .map(source -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    for (String field : List.of(
                            "id", "pointCode", "pointName", "pointType", "coordinateX", "coordinateY")) {
                        if (source.containsKey(field)) {
                            point.put(field, source.get(field));
                        }
                    }
                    return point;
                })
                .toList();
    }

    private Map<String, Object> overviewTaskOverview(List<Map<String, Object>> tasks) {
        Map<String, Object> overview = taskOverview(tasks);
        overview.remove("completedRate");
        return overview;
    }

    private Map<String, Object> overviewAlarms(Map<String, Object> source) {
        Map<String, Object> alarms = mutable(source);
        Map<String, Object> summary = mutable(map(alarms.get("summary")));
        summary.remove("handleRate");
        alarms.put("summary", summary);
        for (String level : List.of("high", "medium", "low")) {
            Map<String, Object> group = mutable(map(alarms.get(level)));
            List<Map<String, Object>> items = list(group.get("items")).stream()
                    .map(this::overviewAlarm)
                    .toList();
            group.put("items", items);
            alarms.put(level, group);
        }
        return alarms;
    }

    private Map<String, Object> overviewAlarm(Map<String, Object> source) {
        Map<String, Object> alarm = mutable(source);
        Map<String, Object> location = map(alarm.get("location"));
        if (!location.isEmpty()) {
            Map<String, Object> compactLocation = mutable(location);
            compactLocation.remove("altitude");
            compactLocation.remove("updatedAt");
            alarm.put("location", compactLocation);
        }
        return alarm;
    }

    private List<String> deviceIdsForMap(String mapId, List<Map<String, Object>> devices) {
        if (mapId == null || devices == null || devices.isEmpty()) {
            return List.of();
        }
        return devices.stream()
                .filter(device -> Objects.equals(mapId, firstString(map(device.get("location")), "mapId")))
                .map(device -> firstString(device, "robotId"))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> withTaskLocationMapIds(
            List<Map<String, Object>> devices,
            List<Map<String, Object>> tasks) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        Map<String, String> taskMapIds = taskMapIdsByRobotId(tasks);
        return devices.stream()
                .map(device -> withTaskLocationMapId(device, taskMapIds))
                .toList();
    }

    private List<Map<String, Object>> withTaskAssociations(
            List<Map<String, Object>> devices,
            List<Map<String, Object>> tasks) {
        return withDeviceTasks(withTaskLocationMapIds(devices, tasks), tasks);
    }

    private List<Map<String, Object>> withDeviceTasks(
            List<Map<String, Object>> devices,
            List<Map<String, Object>> tasks) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }
        Map<String, List<Map<String, Object>>> tasksByRobotId = activeTasksByRobotId(tasks);
        return devices.stream()
                .map(device -> {
                    List<Map<String, Object>> currentTasks = taskArray(device.get("task"));
                    List<Map<String, Object>> associatedTasks = tasksByRobotId.getOrDefault(
                            firstString(device, "robotId"), List.of());
                    Map<String, Object> result = mutable(device);
                    result.put("task", enrichDeviceTasks(currentTasks, associatedTasks));
                    return result;
                })
                .toList();
    }

    private List<Map<String, Object>> enrichDeviceTasks(
            List<Map<String, Object>> currentTasks,
            List<Map<String, Object>> associatedTasks) {
        if (currentTasks.isEmpty()) {
            return associatedTasks;
        }
        return currentTasks.stream()
                .map(currentTask -> {
                    Map<String, Object> associatedTask = associatedTasks.stream()
                            .filter(candidate -> Objects.equals(
                                    string(candidate.get("workflowInstanceId")),
                                    string(currentTask.get("workflowInstanceId"))))
                            .findFirst()
                            .orElse(associatedTasks.size() == 1 ? associatedTasks.get(0) : Map.of());
                    Map<String, Object> result = mutable(associatedTask);
                    currentTask.forEach((key, value) -> {
                        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                            result.put(key, value);
                        }
                    });
                    return result;
                })
                .toList();
    }

    private Map<String, List<Map<String, Object>>> activeTasksByRobotId(List<Map<String, Object>> tasks) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return result;
        }
        for (Map<String, Object> task : tasks) {
            if (!activeDeviceTaskStatus(firstString(task, "status"))) {
                continue;
            }
            Map<String, Object> deviceTask = object(
                    "taskId", task.get("taskId"),
                    "workflowInstanceId", task.get("workflowInstanceId"),
                    "name", firstString(task, "name"),
                    "status", firstString(task, "status"),
                    "timeRange", firstString(task, "timeRange"));
            for (Map<String, Object> equipment : list(task.get("equipmentList"))) {
                String robotId = firstString(equipment, "robotId");
                if (robotId != null && !robotId.isBlank()) {
                    result.computeIfAbsent(robotId, ignored -> new ArrayList<>()).add(deviceTask);
                }
            }
        }
        return result;
    }

    private boolean activeDeviceTaskStatus(String status) {
        return status != null && Set.of(
                "running", "pausing", "paused", "resuming", "terminating")
                .contains(status.toLowerCase(Locale.ROOT));
    }

    private Map<String, String> taskMapIdsByRobotId(List<Map<String, Object>> tasks) {
        Map<String, String> result = new LinkedHashMap<>();
        if (tasks == null) {
            return result;
        }
        for (Map<String, Object> task : tasks) {
            String mapId = firstString(task, "mapId");
            if (mapId == null || mapId.isBlank()) {
                continue;
            }
            for (Map<String, Object> equipment : list(task.get("equipmentList"))) {
                String robotId = firstString(equipment, "robotId");
                if (robotId != null && !robotId.isBlank()) {
                    result.putIfAbsent(robotId, mapId);
                }
            }
        }
        return result;
    }

    private Map<String, Object> withTaskLocationMapId(
            Map<String, Object> device,
            Map<String, String> taskMapIds) {
        if ("FIXED_CAMERA".equals(firstString(device, "sourceType"))) {
            return device;
        }
        Map<String, Object> result = mutable(device);
        Map<String, Object> location = mutable(map(device.get("location")));
        location.put("mapId", taskMapIds.get(firstString(device, "robotId")));
        result.put("location", location);
        return result;
    }

    public Map<String, Object> deviceDetail(String deviceId) {
        OverviewRequestCache cache = new OverviewRequestCache();
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(() -> devices(cache));
        CompletableFuture<PanoramaTasks> tasksFuture = async(() -> taskPayload(cache));
        return withTaskAssociations(
                join(devicesFuture, List.of()),
                join(tasksFuture, new PanoramaTasks(List.of(), List.of())).items()).stream()
                .filter(device -> Objects.equals(deviceId, string(device.get("robotId"))))
                .findFirst()
                .map(this::toDeviceDetail)
                .orElseGet(this::emptyDeviceDetail);
    }

    public Map<String, Object> tasks() {
        OverviewRequestCache cache = new OverviewRequestCache();
        CompletableFuture<PanoramaTasks> tasksFuture = async(() -> taskPayload(cache));
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(() -> devices(cache));
        List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(
                join(tasksFuture, new PanoramaTasks(List.of(), List.of())).items(),
                join(devicesFuture, List.of()));
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

    public Map<String, Object> actionableWorkflowAlarms() {
        List<Map<String, Object>> items = centerClient.actionableWorkflowAlarms().stream()
                .map(this::actionableWorkflowAlarmItem)
                .toList();
        return object(
                "serverTime", now(),
                "total", items.size(),
                "items", items);
    }

    public Map<String, Object> disposeAlarm(String alarmId, Map<String, Object> request) {
        if (alarmId == null || alarmId.isBlank()) {
            throw new IllegalArgumentException("alarmId is required");
        }
        AlarmDisposalStatus disposalStatus = AlarmDisposalStatus.from(string(request == null ? null : request.get("disposalStatus")));
        String handleResult = value(
                string(request == null ? null : request.get("handleResult")),
                disposalStatus.name);
        boolean success = centerClient.handleAlarm(alarmId, disposalStatus.managementAction, handleResult);
        return disposalResponse(alarmId, disposalStatus, success);
    }

    public Map<String, Object> handleWorkflowAlarm(String alarmId, Map<String, Object> request) {
        if (alarmId == null || alarmId.isBlank()) {
            throw new IllegalArgumentException("alarmId is required");
        }
        AlarmDisposalStatus disposalStatus = AlarmDisposalStatus.from(string(request == null ? null : request.get("disposalStatus")));
        String handleResult = value(
                string(request == null ? null : request.get("handleResult")),
                disposalStatus.name);
        boolean success = centerClient.handleWorkflowAlarm(alarmId, disposalStatus.managementAction, handleResult);
        return disposalResponse(alarmId, disposalStatus, success);
    }

    private Map<String, Object> disposalResponse(
            String alarmId,
            AlarmDisposalStatus disposalStatus,
            boolean success) {
        return object(
                "success", success,
                "serverTime", now(),
                "alarmId", alarmId,
                "disposalStatus", disposalStatus.code,
                "disposalStatusName", disposalStatus.name,
                "status", success ? disposalStatus.alarmStatus : null,
                "message", success ? "告警处置状态已更新" : "告警处置状态更新失败");
    }

    private List<Map<String, Object>> devices(OverviewRequestCache cache) {
        CompletableFuture<List<Map<String, Object>>> managementDevicesFuture = async(centerClient::devices);
        CompletableFuture<List<Map<String, Object>>> registeredRobotsFuture = async(centerClient::registeredRobots);
        CompletableFuture<List<Map<String, Object>>> fixedCamerasFuture = cache.allFixedCamerasFuture();
        CompletableFuture<List<Map<String, Object>>> deviceTypeOptionsFuture = async(centerClient::deviceTypeOptions);
        List<Map<String, Object>> managementDevices = join(managementDevicesFuture, List.of());
        CompletableFuture<Map<String, Map<String, Object>>> statusBySerialFuture = async(() -> statusBySerial(managementDevices));
        List<Map<String, Object>> validManagementDevices = managementDevices.stream()
                .filter(this::hasDeviceId)
                .toList();
        List<CompletableFuture<Map<String, Object>>> deviceSourceFutures = validManagementDevices.stream()
                .map(device -> async(() -> deviceSource(device)))
                .toList();
        Map<String, Map<String, Object>> statusBySerial = join(statusBySerialFuture, Map.of());
        Map<String, String> deviceTypeNames = deviceTypeNames(join(deviceTypeOptionsFuture, List.of()));
        List<String> appendedRobotIds = new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < validManagementDevices.size(); index++) {
            Map<String, Object> managementDevice = validManagementDevices.get(index);
            String robotId = firstString(managementDevice, "serialNumber");
            Map<String, Object> realtimeStatus = statusBySerial.getOrDefault(robotId, Map.of());
            Map<String, Object> source = join(deviceSourceFutures.get(index), managementDevice);
            Map<String, Object> device = device(source, realtimeStatus, deviceTypeNames);
            result.add(device);
            appendRobotId(appendedRobotIds, device.get("robotId"));
        }
        join(registeredRobotsFuture, List.<Map<String, Object>>of()).stream()
                .filter(this::hasDeviceId)
                .filter(robot -> !containsRobotId(appendedRobotIds, firstValue(robot, "robotId", "serialNumber")))
                .map(robot -> registeredRobotDevice(robot, deviceTypeNames))
                .forEach(device -> {
                    result.add(device);
                    appendRobotId(appendedRobotIds, device.get("robotId"));
                });
        join(fixedCamerasFuture, List.<Map<String, Object>>of()).stream()
                .filter(camera -> firstValue(camera, "cameraId", "id") != null)
                .map(this::fixedCameraDevice)
                .forEach(result::add);
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

    private boolean hasDeviceId(Map<String, Object> source) {
        return firstValue(source, "serialNumber", "robotId", "id") != null;
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
            Map<String, String> deviceTypeNames) {
        Map<String, Object> basic = map(path(realtimeStatus, "status", "basic"));
        Map<String, Object> motion = map(path(realtimeStatus, "status", "motion"));
        Map<String, Object> localization = map(path(realtimeStatus, "status", "localization"));
        Map<String, Object> energy = map(path(realtimeStatus, "status", "energy"));
        Map<String, Object> control = map(path(realtimeStatus, "status", "control"));
        Map<String, Object> task = map(path(realtimeStatus, "status", "task"));

        Object robotId = firstValue(source, "serialNumber", "robotId", "id");
        String status = onlineStatus(string(realtimeStatus.get("onlineStatus")));
        List<Map<String, Object>> mountedDevices = mountedDevices(source, status);
        Object alarmLevel = alarmLevel(basic);
        Object fault = fault(basic);
        String name = firstString(source, "deviceName", "name");

        return object(
                "robotId", robotId,
                "clientId", firstValue(source, "authMqttClientId", "clientId"),
                "name", name,
                "type", typeName(firstString(source, "deviceType", "typeCode", "type"), deviceTypeNames),
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
                "controlMode", normalizeControlMode(firstString(control, "controlMode")),
                "controlModeName", controlModeName(normalizeControlMode(firstString(control, "controlMode"))),
                "mountedDeviceCount", mountedDeviceCount(source, mountedDevices),
                "speed", number(motion.get("speed")),
                "location", location(localization, realtimeStatus),
                "mapDisplay", mapDisplay(name, status, alarmLevel),
                "task", deviceTasks(task));
    }

    private Map<String, Object> registeredRobotDevice(Map<String, Object> source, Map<String, String> deviceTypeNames) {
        String robotId = firstString(source, "robotId", "serialNumber", "id");
        String status = onlineStatus(firstString(source, "status", "onlineStatus"));
        String name = value(firstString(source, "name", "deviceName"), robotId);
        String typeCode = firstString(source, "typeCode", "deviceType");
        List<Map<String, Object>> mountedDevices = list(firstValue(source, "devices", "mountedDevices"));
        Object alarmLevel = null;
        return object(
                "robotId", robotId,
                "clientId", firstValue(source, "clientId", "authMqttClientId"),
                "name", name,
                "type", typeName(typeCode, deviceTypeNames),
                "typeCode", typeCode,
                "vendor", firstValue(source, "manufacturer", "vendor"),
                "model", firstValue(source, "model"),
                "status", status,
                "battery", number(source.get("battery")),
                "lastHeartbeatAt", formatTime(firstString(source, "lastHeartbeatAt", "timestamp")),
                "cameras", registeredRobotCameras(source, robotId),
                "mountedDevices", mountedDevices,
                "stateSeq", number(source.get("stateSeq")),
                "fault", "fault".equals(status),
                "alarmLevel", alarmLevel,
                "controlMode", normalizeControlMode(firstString(source, "controlMode")),
                "controlModeName", value(firstString(source, "controlModeName"), controlModeName(normalizeControlMode(firstString(source, "controlMode")))),
                "mountedDeviceCount", mountedDevices.size(),
                "speed", number(source.get("speed")),
                "location", registeredRobotLocation(source),
                "mapDisplay", mapDisplay(name, status, alarmLevel),
                "task", List.of());
    }

    private List<Map<String, Object>> registeredRobotCameras(Map<String, Object> source, String robotId) {
        List<Map<String, Object>> cameras = list(source.get("cameras"));
        if (!cameras.isEmpty()) {
            return cameras.stream()
                    .map(camera -> object(
                            "cameraId", firstValue(camera, "cameraId", "id", "deviceId"),
                            "deviceId", firstValue(camera, "deviceId", "cameraId", "id"),
                            "groupType", firstValue(camera, "groupType"),
                            "name", firstString(camera, "name", "cameraName"),
                            "quality", value(firstString(camera, "quality"), "sub")))
                    .toList();
        }
        if (robotId == null || robotId.isBlank()) {
            return List.of();
        }
        return List.of(camera(robotId, robotId, "body", "本体相机"));
    }

    private Map<String, Object> registeredRobotLocation(Map<String, Object> source) {
        Map<String, Object> location = map(source.get("location"));
        return object(
                "mapId", firstValue(location, "mapId", "mapID"),
                "lng", number(firstValue(location, "lng", "longitude")),
                "lat", number(firstValue(location, "lat", "latitude")),
                "altitude", number(firstValue(location, "altitude")),
                "x", number(firstValue(location, "coordinateX", "x")),
                "y", number(firstValue(location, "coordinateY", "y")),
                "z", number(firstValue(location, "coordinateZ", "z")),
                "address", firstString(location, "address"),
                "updatedAt", formatTime(value(firstString(location, "updatedAt"), firstString(source, "timestamp", "lastHeartbeatAt"))));
    }

    private Map<String, Object> fixedCameraDevice(Map<String, Object> source) {
        String cameraId = firstString(source, "cameraId", "id");
        boolean enabled = booleanValue(source.get("enabled"));
        String defaultQuality = firstString(source, "subStreamUrl") == null ? "main" : "sub";
        boolean playable = enabled && (firstString(source, "mainStreamUrl") != null || firstString(source, "subStreamUrl") != null);
        String status = enabled ? "online" : "offline";
        return object(
                "robotId", cameraId,
                "equipmentId", cameraId,
                "cameraId", cameraId,
                "clientId", "fixed-camera-gateway",
                "name", firstString(source, "cameraName", "name"),
                "type", "固定摄像头",
                "typeCode", "FIXED_CAMERA",
                "equipmentType", "FIXED_CAMERA",
                "sourceType", "FIXED_CAMERA",
                "status", status,
                "battery", null,
                "lastHeartbeatAt", null,
                "cameras", List.of(camera(cameraId, cameraId, "fixed_camera", firstString(source, "cameraName", "name"), defaultQuality)),
                "mountedDevices", List.of(),
                "stateSeq", null,
                "fault", false,
                "alarmLevel", null,
                "controlMode", null,
                "controlModeName", null,
                "mountedDeviceCount", 0,
                "speed", null,
                "location", fixedCameraLocation(source),
                "mapDisplay", fixedCameraMapDisplay(source, enabled),
                "task", List.of(),
                "mapId", firstValue(source, "mapId"),
                "locationDescription", firstValue(source, "locationDescription"),
                "coordinateX", number(source.get("coordinateX")),
                "coordinateY", number(source.get("coordinateY")),
                "headingYaw", number(source.get("headingYaw")),
                "protocolType", firstString(source, "protocolType"),
                "defaultQuality", defaultQuality,
                "playable", playable,
                "showControlCenter", false,
                "showController", false);
    }

    private Map<String, Object> fixedCameraLocation(Map<String, Object> source) {
        return object(
                "mapId", firstValue(source, "mapId"),
                "lng", null,
                "lat", null,
                "altitude", null,
                "x", number(source.get("coordinateX")),
                "y", number(source.get("coordinateY")),
                "z", null,
                "address", firstString(source, "locationDescription"),
                "updatedAt", null);
    }

    private Map<String, Object> fixedCameraMapDisplay(Map<String, Object> source, boolean enabled) {
        return object(
                "visible", true,
                "label", firstString(source, "cameraName", "name"),
                "status", enabled ? "enabled" : "disabled",
                "icon", enabled ? "robot-camera-normal" : "robot-camera-off",
                "headingYaw", number(source.get("headingYaw")));
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
        return camera(cameraId, deviceId, groupType, name, "sub");
    }

    private Map<String, Object> camera(String cameraId, String deviceId, String groupType, String name, String quality) {
        return object(
                "cameraId", cameraId,
                "deviceId", deviceId,
                "groupType", groupType,
                "name", name,
                "quality", quality);
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
                "mapId", firstValue(localization, "mapId", "mapID"),
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
                "mapId", null,
                "lng", null,
                "lat", null,
                "altitude", null,
                "x", null,
                "y", null,
                "z", null,
                "address", null,
                "updatedAt", null);
    }

    private List<Map<String, Object>> deviceTasks(Map<String, Object> realtimeTask) {
        Object taskId = realtimeTask.get("taskInstanceId");
        if (taskId == null) {
            return List.of();
        }
        return List.of(object(
                "taskId", null,
                "workflowInstanceId", taskId,
                "name", firstString(realtimeTask, "taskName", "workflowName"),
                "status", statusCode(firstString(realtimeTask, "taskStatus", "status")),
                "timeRange", null));
    }

    private PanoramaTasks taskPayload(OverviewRequestCache cache) {
        CompletableFuture<List<Map<String, Object>>> taskPlansFuture = async(centerClient::taskWorkflowPlans);
        CompletableFuture<List<Map<String, Object>>> taskInstancesFuture = async(centerClient::taskWorkflowInstances);
        List<Map<String, Object>> taskPlans = join(taskPlansFuture, List.of());
        List<Map<String, Object>> taskInstances = join(taskInstancesFuture, List.of());
        if (taskPlans.isEmpty()) {
            return new PanoramaTasks(List.of(), taskInstances);
        }
        TaskInstanceResolver taskInstanceResolver = new TaskInstanceResolver(taskInstances);
        TaskRouteResolver routeResolver = new TaskRouteResolver(taskPlans, cache);
        for (int index = 0; index < taskPlans.size(); index++) {
            Map<String, Object> plan = taskPlans.get(index);
            taskInstanceResolver.prefetch(planWorkflowInstanceId(plan));
            routeResolver.prefetch(plan, index);
        }
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (int index = 0; index < taskPlans.size(); index++) {
            Map<String, Object> sourceTask = taskPlans.get(index);
            int taskIndex = index;
            futures.add(async(() -> taskItem(sourceTask, routeResolver.resolve(sourceTask, taskIndex), taskInstanceResolver)));
        }
        List<Map<String, Object>> result = futures.stream()
                .map(future -> join(future, null))
                .filter(Objects::nonNull)
                .toList();
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
        String rawStatus = taskPlanStatus(source, instance);
        String startTime = formatTime(value(
                firstString(instance, "startedAt"),
                firstString(source, "startedAt", "lastStartedAt", "startTime")));
        String endTime = formatTime(value(
                firstString(instance, "completedAt"),
                firstString(source, "completedAt", "lastCompletedAt", "endTime")));
        return object(
                "taskId", firstValue(source, "id", "taskId"),
                "workflowInstanceId", workflowInstanceId,
                "name", firstString(source, "planName", "workflowName", "name"),
                "executionMode", firstValue(source, "executionMode"),
                "expectedDurationSeconds", firstValue(source, "expectedDurationSeconds"),
                "status", taskStatusCode(rawStatus),
                "statusName", taskStatusName(rawStatus),
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
                            "status", null))
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
                            "status", null))
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

    private List<Map<String, Object>> withEquipmentOnlineStatuses(
            List<Map<String, Object>> tasks,
            List<Map<String, Object>> devices) {
        Map<String, String> statusByRobotId = new LinkedHashMap<>();
        for (Map<String, Object> device : devices) {
            String robotId = firstString(device, "robotId");
            String status = equipmentOnlineStatus(device.get("status"));
            if (robotId != null && status != null) {
                statusByRobotId.put(robotId, status);
            }
        }
        return tasks.stream()
                .map(task -> {
                    Map<String, Object> result = mutable(task);
                    List<Map<String, Object>> equipment = list(task.get("equipmentList")).stream()
                            .map(item -> {
                                Map<String, Object> equipmentItem = mutable(item);
                                equipmentItem.put("status", statusByRobotId.get(firstString(item, "robotId")));
                                return equipmentItem;
                            })
                            .toList();
                    result.put("equipmentList", equipment);
                    return result;
                })
                .toList();
    }

    private String equipmentOnlineStatus(Object source) {
        String status = onlineStatus(string(source));
        return switch (status == null ? "" : status) {
            case "online", "offline", "fault" -> status;
            default -> null;
        };
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
                "categoryName", categoryName(firstString(source, "sourceType", "alarmType", "category")),
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

    private Map<String, Object> actionableWorkflowAlarmItem(Map<String, Object> source) {
        return object(
                "alarmId", firstValue(source, "alarmId", "id", "alarmCode"),
                "title", firstString(source, "title"),
                "content", firstString(source, "content"),
                "categoryName", categoryName(firstString(source, "sourceType")),
                "level", levelCode(firstString(source, "severity", "level")),
                "levelName", levelName(firstString(source, "severity", "level")),
                "eventTime", formatTime(firstString(source, "occurredAt", "eventTime")),
                "robotId", firstValue(source, "serialNumber", "deviceCode", "robotId"),
                "deviceName", firstString(source, "deviceName"),
                "snapshotUrl", snapshotUrl(source),
                "workflowInstanceId", firstValue(source, "workflowInstanceId"),
                "taskName", firstString(source, "workflowName", "taskName"),
                "humanTaskId", firstValue(source, "humanTaskId"),
                "humanTaskName", firstString(source, "humanTaskName"));
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
        String imageFileId = firstScalarString(source.get("imageFileIds"));
        if (imageFileId != null) {
            return "/api/bigscreen/control/files/" + imageFileId + "/content";
        }
        String imageUrl = firstScalarString(source.get("imageUrls"));
        if (imageUrl != null) {
            return imageUrl;
        }
        imageUrl = firstString(source, "imageUrl");
        if (imageUrl != null) {
            return imageUrl;
        }
        imageFileId = firstString(source, "imageFileId");
        return imageFileId == null ? null : "/api/bigscreen/control/files/" + imageFileId + "/content";
    }

    private String firstScalarString(Object value) {
        return scalarList(value).stream()
                .map(this::string)
                .filter(Objects::nonNull)
                .filter(item -> !item.isBlank())
                .findFirst()
                .orElse(null);
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

    private Map<String, Object> alarmStats(Map<String, Object> alarms) {
        return object(
                "high", list(map(alarms.get("high")).get("items")).size(),
                "medium", list(map(alarms.get("medium")).get("items")).size(),
                "low", list(map(alarms.get("low")).get("items")).size());
    }

    private Map<String, Object> taskOverview(List<Map<String, Object>> tasks) {
        long running = tasks.stream().filter(task -> "running".equals(task.get("status"))).count();
        long pending = tasks.stream().filter(task -> "waiting".equals(task.get("status"))).count();
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
                .filter(device -> string(device.get("typeCode")) != null)
                .collect(Collectors.groupingBy(device -> string(device.get("typeCode")), LinkedHashMap::new, Collectors.toList()));
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
                "controlModeName", device.get("controlModeName"),
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
                "controlModeName", null,
                "speed", null,
                "location", emptyLocation(),
                "mountedDeviceCount", null,
                "mountedDevices", List.of(),
                "currentTask", List.of(),
                "actions", emptyActions());
    }

    private Map<String, Object> patrolOverview(
            List<Map<String, Object>> taskInstances,
            Map<String, Object> mileageSummary) {
        long durationSeconds = taskInstances.stream()
                .filter(this::todayTaskInstance)
                .map(this::durationSeconds)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        Double durationToday = durationSeconds == 0 ? null : oneDecimal(durationSeconds / 3600.0);
        Number totalMeters = number(mileageSummary.get("totalMeters"));
        Double mileageToday = Boolean.TRUE.equals(mileageSummary.get("hasData")) && totalMeters != null
                ? oneDecimal(totalMeters.doubleValue() / 1000.0)
                : null;
        return object(
                "durationToday", durationToday,
                "durationUnit", durationToday == null ? null : "小时",
                "mileageToday", mileageToday,
                "mileageUnit", mileageToday == null ? null : "KM");
    }

    private Map<String, Object> todayMileageSummary() {
        LocalDate today = LocalDate.now(CHINA_ZONE);
        return centerClient.mileageSummary(
                LocalDateTime.of(today, java.time.LocalTime.MIN).format(DATE_TIME_FORMATTER),
                LocalDateTime.now(CHINA_ZONE).format(DATE_TIME_FORMATTER),
                List.of());
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

    private Map<String, Object> emptyAlarmsPayload() {
        return object(
                "total", 0,
                "summary", alarmSummary(List.of()),
                "high", alarmGroup(List.of()),
                "medium", alarmGroup(List.of()),
                "low", alarmGroup(List.of()));
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            try {
                SecurityContextHolder.setContext(context);
                return supplier.get();
            } finally {
                SecurityContextHolder.setContext(previousContext);
            }
        }, IO_EXECUTOR);
    }

    private <T> T join(CompletableFuture<T> future, T fallback) {
        try {
            T value = future.join();
            return value == null ? fallback : value;
        } catch (CompletionException exception) {
            return fallback;
        }
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

    private final class OverviewRequestCache {

        private final Map<String, CompletableFuture<List<Map<String, Object>>>> mapPointsByMapId = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<List<Map<String, Object>>>> pathPointsByPathId = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<List<Map<String, Object>>>> fixedCamerasByMapId = new ConcurrentHashMap<>();
        private volatile CompletableFuture<List<Map<String, Object>>> allFixedCamerasFuture;

        private List<Map<String, Object>> mapPoints(String mapId) {
            return join(mapPointsFuture(mapId), List.of());
        }

        private CompletableFuture<List<Map<String, Object>>> mapPointsFuture(String mapId) {
            return cachedList(mapPointsByMapId, mapId, centerClient::mapPoints);
        }

        private List<Map<String, Object>> pathPoints(String pathId) {
            return join(cachedList(pathPointsByPathId, pathId, centerClient::pathPoints), List.of());
        }

        private CompletableFuture<List<Map<String, Object>>> fixedCamerasFuture(String mapId) {
            if (mapId == null || mapId.isBlank()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return fixedCamerasByMapId.computeIfAbsent(mapId, key -> allFixedCamerasFuture()
                    .thenApply(cameras -> cameras.stream()
                            .filter(camera -> Objects.equals(key, firstString(camera, "mapId")))
                            .toList()));
        }

        private CompletableFuture<List<Map<String, Object>>> allFixedCamerasFuture() {
            CompletableFuture<List<Map<String, Object>>> current = allFixedCamerasFuture;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (allFixedCamerasFuture == null) {
                    allFixedCamerasFuture = async(centerClient::fixedCameras);
                }
                return allFixedCamerasFuture;
            }
        }

        private CompletableFuture<List<Map<String, Object>>> cachedList(
                Map<String, CompletableFuture<List<Map<String, Object>>>> cache,
                String key,
                Function<String, List<Map<String, Object>>> loader) {
            if (key == null || key.isBlank()) {
                return CompletableFuture.completedFuture(List.of());
            }
            return cache.computeIfAbsent(key, value -> async(() -> loader.apply(value)));
        }
    }

    private final class TaskInstanceResolver {

        private final Map<String, Map<String, Object>> instancesById = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<Map<String, Object>>> instanceFuturesById = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<Map<String, Object>>> replaysById = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<List<Map<String, Object>>>> deviceTasksByWorkflowInstanceId = new ConcurrentHashMap<>();

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
            Map<String, Object> existing = instancesById.get(id);
            if (existing != null) {
                return existing;
            }
            Map<String, Object> loaded = join(instanceFuturesById.computeIfAbsent(id,
                    value -> async(() -> loadInstance(value))), Map.of());
            if (!loaded.isEmpty()) {
                instancesById.putIfAbsent(id, loaded);
            }
            return loaded;
        }

        private void prefetch(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return;
            }
            if (!instancesById.containsKey(id)) {
                instanceFuturesById.computeIfAbsent(id, value -> async(() -> loadInstance(value)));
            }
            replaysById.computeIfAbsent(id,
                    value -> async(() -> centerClient.taskWorkflowReplay(value).orElse(Map.of())));
            deviceTasksByWorkflowInstanceId.computeIfAbsent(id,
                    value -> async(() -> centerClient.deviceTaskInstances(value)));
        }

        private Map<String, Object> replay(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return Map.of();
            }
            return join(replaysById.computeIfAbsent(id,
                    value -> async(() -> centerClient.taskWorkflowReplay(value).orElse(Map.of()))), Map.of());
        }

        private List<Map<String, Object>> deviceTaskInstances(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return List.of();
            }
            return join(deviceTasksByWorkflowInstanceId.computeIfAbsent(id,
                    value -> async(() -> centerClient.deviceTaskInstances(value))), List.of());
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
        private final Map<String, CompletableFuture<TaskRouteData>> routesByDefinitionId = new ConcurrentHashMap<>();
        private final OverviewRequestCache cache;

        private TaskRouteResolver(List<Map<String, Object>> plans, OverviewRequestCache cache) {
            this.plans = plans == null ? List.of() : plans;
            this.cache = cache;
            this.plansById = indexPlans("id", "planId", "workflowPlanId", "taskWorkflowPlanId", "code");
            this.plansByName = indexPlans("planName", "workflowName", "name");
        }

        private TaskRouteData resolve(Map<String, Object> source, int index) {
            String workflowDefinitionId = workflowDefinitionId(source, index);
            if (workflowDefinitionId == null || workflowDefinitionId.isBlank()) {
                return TaskRouteData.empty();
            }
            return join(routesByDefinitionId.computeIfAbsent(workflowDefinitionId,
                    value -> async(() -> routeData(value))), TaskRouteData.empty());
        }

        private void prefetch(Map<String, Object> source, int index) {
            String workflowDefinitionId = workflowDefinitionId(source, index);
            if (workflowDefinitionId != null && !workflowDefinitionId.isBlank()) {
                routesByDefinitionId.computeIfAbsent(workflowDefinitionId,
                        value -> async(() -> routeData(value)));
            }
        }

        private String workflowDefinitionId(Map<String, Object> source, int index) {
            String workflowDefinitionId = value(
                    firstString(source, "workflowDefinitionId", "definitionId"),
                    firstString(map(source.get("workflowDefinition")), "id", "workflowDefinitionId"));
            if (workflowDefinitionId == null) {
                workflowDefinitionId = firstString(plan(source, index), "workflowDefinitionId", "definitionId");
            }
            return workflowDefinitionId;
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
                    : cache.mapPoints(string(mapId));
            List<Map<String, Object>> pathPointRefs = pathId == null
                    ? List.of()
                    : cache.pathPoints(string(pathId));
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
        String normalized = healthStatus.toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(normalized)) {
            return false;
        }
        if (normalized.contains("ERROR")
                || normalized.contains("FAULT")
                || normalized.contains("异常")
                || normalized.contains("故障")) {
            return true;
        }
        return null;
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
            case "HANDLED" -> "handled";
            case "ACKNOWLEDGED" -> "unhandled";
            case "FALSE_ALARM" -> "false_alarm";
            case "FAILED", "ERROR" -> "failed";
            case "UNHANDLED", "NEW" -> "unhandled";
            case "HANDLING" -> "handling";
            default -> source.toLowerCase(Locale.ROOT);
        };
    }

    private String taskStatusCode(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "WAITING", "PENDING", "PREPARING" -> "waiting";
            case "RUNNING" -> "running";
            case "PAUSING" -> "pausing";
            case "PAUSED" -> "paused";
            case "RESUMING" -> "resuming";
            case "TERMINATING" -> "terminating";
            case "CONTROL_FAILED", "FAILED" -> "failed";
            case "COMPLETED" -> "completed";
            case "TERMINATED" -> "terminated";
            default -> null;
        };
    }

    private String taskPlanStatus(Map<String, Object> source, Map<String, Object> instance) {
        String activeStatus = firstString(source, "activeWorkflowInstanceStatus");
        if (activeStatus != null && Set.of(
                "PREPARING", "RUNNING", "PAUSING", "PAUSED", "RESUMING", "TERMINATING", "CONTROL_FAILED")
                .contains(activeStatus.toUpperCase(Locale.ROOT))) {
            return activeStatus;
        }
        return value(
                firstString(source, "executionStatus"),
                value(firstString(instance, "status"), firstString(source, "lastResultStatus")));
    }

    private String taskStatusName(String source) {
        String status = taskStatusCode(source);
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "waiting" -> "待执行";
            case "running" -> "执行中";
            case "pausing" -> "暂停中";
            case "paused" -> "已暂停";
            case "resuming" -> "恢复中";
            case "terminating" -> "终止中";
            case "failed" -> "执行失败";
            case "completed" -> "已完成";
            case "terminated" -> "已终止";
            default -> null;
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
            case "COMPONENT" -> "组件告警";
            default -> source;
        };
    }

    private Map<String, String> deviceTypeNames(List<Map<String, Object>> options) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> option : options) {
            String code = firstString(option, "value", "itemValue", "itemCode", "code");
            String name = firstString(option, "label", "itemName", "name");
            if (code != null && !code.isBlank() && name != null && !name.isBlank()) {
                result.put(code.toUpperCase(Locale.ROOT), name);
            }
        }
        return result;
    }

    private String typeName(String typeCode) {
        return typeName(typeCode, Map.of());
    }

    private String typeName(String typeCode, Map<String, String> deviceTypeNames) {
        if (typeCode == null || typeCode.isBlank()) {
            return null;
        }
        return deviceTypeNames.getOrDefault(typeCode.toUpperCase(Locale.ROOT), typeCode);
    }

    private boolean handled(String status) {
        return "handled".equals(status) || "false_alarm".equals(status);
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

    private void appendRobotId(List<String> robotIds, Object value) {
        String robotId = string(value);
        if (robotId != null && !robotId.isBlank()) {
            robotIds.add(robotId);
        }
    }

    private boolean containsRobotId(List<String> robotIds, Object value) {
        String robotId = string(value);
        return robotId != null && robotIds.contains(robotId);
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

    private String normalizeControlMode(String controlMode) {
        if (controlMode == null || controlMode.isBlank()) {
            return null;
        }
        return "手动模式".equals(controlMode) ? "手动模式" : "导航模式";
    }

    private String controlModeName(String controlMode) {
        return controlMode;
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
        IMMEDIATE_DISPOSAL("IMMEDIATE_DISPOSAL", "立即处置", "handled", "HANDLE_NOW"),
        FALSE_ALARM("FALSE_ALARM", "误报", "false_alarm", "FALSE_ALARM");

        private final String code;
        private final String name;
        private final String alarmStatus;
        private final String managementAction;

        AlarmDisposalStatus(String code, String name, String alarmStatus, String managementAction) {
            this.code = code;
            this.name = name;
            this.alarmStatus = alarmStatus;
            this.managementAction = managementAction;
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
