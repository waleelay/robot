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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class PanoramaService {

    private static final ZoneOffset CHINA_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long STATS_CACHE_TTL_MILLIS = 3000;
    /**
     * 同一登录主体的首屏在 5 秒窗口内复用同一份成功快照。它既避免高频刷新击穿 Management，
     * 也不把失败结果缓存给后续用户请求。
     */
    private static final long OVERVIEW_CACHE_TTL_MILLIS = 5000;
    private static final long OVERVIEW_TIMEOUT_MILLIS = 8000;
    private static final long STATS_TIMEOUT_MILLIS = 5000;
    private static final int SNAPSHOT_CACHE_MAX_SIZE = 256;
    private static final int IN_FLIGHT_MAX_SIZE = 128;
    private static final int ALARM_PAGE_SIZE = 10;
    /** 顶层编排允许等待子 I/O，但绝不占用子 I/O 执行器。 */
    private static final ThreadPoolExecutor OVERVIEW_EXECUTOR = new ThreadPoolExecutor(
            5,
            5,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(16),
            namedDaemonThreadFactory("panorama-overview"),
            new ThreadPoolExecutor.AbortPolicy());
    /**
     * 只执行下游 I/O。使用直接移交队列，使嵌套任务在有容量时立即扩容；容量耗尽时快速降级，
     * 不能把子任务排到正在等待它的父任务后面。
     */
    private static final ThreadPoolExecutor IO_EXECUTOR = new ThreadPoolExecutor(
            8,
            32,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            namedDaemonThreadFactory("panorama-io"),
            new ThreadPoolExecutor.AbortPolicy());
    /** 任务事件刷新独立限流，繁忙时不得占用全景页面查询的 I/O 线程。 */
    private static final ThreadPoolExecutor TASK_EVENT_IO_EXECUTOR = new ThreadPoolExecutor(
            2,
            4,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            namedDaemonThreadFactory("panorama-task-event-io"),
            new ThreadPoolExecutor.AbortPolicy());
    /** WebSocket 统计刷新独立于首屏下游容量，健康或任务事件不能挤占首屏。 */
    private static final ThreadPoolExecutor STATS_EXECUTOR = new ThreadPoolExecutor(
            4,
            4,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),
            namedDaemonThreadFactory("panorama-stats"),
            new ThreadPoolExecutor.AbortPolicy());
    private static final ThreadPoolExecutor STATS_IO_EXECUTOR = new ThreadPoolExecutor(
            4,
            8,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            namedDaemonThreadFactory("panorama-stats-io"),
            new ThreadPoolExecutor.AbortPolicy());
    private static final ThreadLocal<Long> OVERVIEW_DEADLINE_NANOS = new ThreadLocal<>();
    private static final ThreadLocal<ThreadPoolExecutor> REQUEST_IO_EXECUTOR = new ThreadLocal<>();

    private final PanoramaCenterClient centerClient;
    private final ObjectMapper objectMapper;
    private final BoundedTtlCache<String, Object> statsCache =
            new BoundedTtlCache<>(SNAPSHOT_CACHE_MAX_SIZE, STATS_CACHE_TTL_MILLIS);
    private final Map<String, CompletableFuture<Object>> statsPartInFlight = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> statsSnapshotInFlight = new ConcurrentHashMap<>();
    private final BoundedTtlCache<String, Map<String, Object>> overviewCache =
            new BoundedTtlCache<>(SNAPSHOT_CACHE_MAX_SIZE, OVERVIEW_CACHE_TTL_MILLIS);
    private final Map<String, CompletableFuture<Map<String, Object>>> overviewInFlight = new ConcurrentHashMap<>();

    public PanoramaService(PanoramaCenterClient centerClient, ObjectMapper objectMapper) {
        this.centerClient = centerClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> overview() {
        String cacheKey = "overview:" + statsUserKey();
        Optional<Map<String, Object>> cached = overviewCache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }
        if (!overviewInFlight.containsKey(cacheKey) && overviewInFlight.size() >= IN_FLIGHT_MAX_SIZE) {
            throw new IllegalStateException("大屏总览并发身份已达上限，请稍后重试");
        }
        SecurityContext callerContext = copySecurityContext(SecurityContextHolder.getContext());
        CompletableFuture<Map<String, Object>> shared = overviewInFlight.computeIfAbsent(cacheKey, key -> {
            CompletableFuture<Map<String, Object>> created;
            try {
                created = CompletableFuture.supplyAsync(
                        () -> loadOverviewWithSecurityContext(callerContext), OVERVIEW_EXECUTOR);
            } catch (RejectedExecutionException error) {
                throw new IllegalStateException("大屏总览请求繁忙，请稍后重试", error);
            }
            created.whenComplete((value, error) -> {
                if (error == null && value != null) {
                    overviewCache.put(key, value);
                }
                overviewInFlight.remove(key, created);
            });
            return created;
        });
        try {
            return joinShared(shared, OVERVIEW_TIMEOUT_MILLIS);
        } catch (RuntimeException exception) {
            // 原 future 完成与 whenComplete 清理存在极短竞态；失败等待者返回前先条件删除，
            // 保证紧接着的重试不会再次复用同一个失败结果。
            overviewInFlight.remove(cacheKey, shared);
            throw exception;
        }
    }

    private SecurityContext copySecurityContext(SecurityContext source) {
        SecurityContext copy = SecurityContextHolder.createEmptyContext();
        copy.setAuthentication(source == null ? null : source.getAuthentication());
        return copy;
    }

    private Map<String, Object> loadOverviewWithSecurityContext(SecurityContext callerContext) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(callerContext);
            return loadOverviewWithinDeadline();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> joinShared(
            CompletableFuture<Map<String, Object>> future,
            long timeoutMillis) {
        try {
            return future.get(timeoutMillis + 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大屏聚合等待被中断", error);
        } catch (java.util.concurrent.TimeoutException error) {
            future.cancel(true);
            throw new IllegalStateException("大屏聚合超过总时限", error);
        } catch (java.util.concurrent.ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new CompletionException(cause);
        }
    }

    private Map<String, Object> loadOverviewWithinDeadline() {
        Long previousDeadline = OVERVIEW_DEADLINE_NANOS.get();
        ThreadPoolExecutor previousIoExecutor = REQUEST_IO_EXECUTOR.get();
        OVERVIEW_DEADLINE_NANOS.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OVERVIEW_TIMEOUT_MILLIS));
        REQUEST_IO_EXECUTOR.set(IO_EXECUTOR);
        try {
            return overviewWithinDeadline();
        } finally {
            restoreOverviewDeadline(previousDeadline);
            restoreRequestIoExecutor(previousIoExecutor);
        }
    }

    private Map<String, Object> overviewWithinDeadline() {
        OverviewRequestCache cache = new OverviewRequestCache();
        Map<String, Object> overview = object("serverTime", now());
        CompletableFuture<List<Map<String, Object>>> devicesFuture = asyncOverview(() -> devices(cache));
        CompletableFuture<PanoramaTasks> tasksFuture = asyncOverview(this::taskSummaries);
        CompletableFuture<List<Map<String, Object>>> mapsFuture = asyncOverview(centerClient::enabledMaps);
        CompletableFuture<Map<String, Object>> alarmsFuture = asyncOverview(this::alarmsPayload);
        CompletableFuture<Map<String, Object>> mileageFuture = asyncOverview(this::todayMileageSummary);

        // 查询失败不等于地图已移除，不能用空列表触发前端切换地图。
        List<Map<String, Object>> maps = joinRequired(mapsFuture);
        List<Map<String, Object>> rawDevices = joinRequired(devicesFuture);
        PanoramaTasks panoramaTasks = join(tasksFuture, unavailableTasks("TASK_AGGREGATION_FAILED"));
        List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(panoramaTasks.items(), rawDevices);
        // 管理端地图 ID 与边缘 SLAM 地图 ID 不是同一命名空间；首屏只用任务摘要修正地图归属，
        // 不再把重复的任务对象挂回 devices[]。
        List<Map<String, Object>> devices = withTaskLocationMapIds(rawDevices, tasks);
        cacheStatsValue("devices", devices);
        cacheStatsValue("tasks", panoramaTasks);
        overview.put("devices", overviewDevices(devices));
        overview.put("deviceStats", deviceStats(devices));
        overview.put("deviceTypeStats", deviceTypeStats(devices));

        overview.put("patrolOverview", patrolOverview(
                panoramaTasks.instances(), join(mileageFuture, Map.of())));
        overview.put("tasks", overviewTasks(tasks));
        overview.put("taskOverview", overviewTaskOverview(tasks));
        overview.put("dataQuality", object("tasks", panoramaTasks.dataQuality()));

        overview.put("map", maps.stream().map(this::overviewMap).toList());

        overview.put("alarms", overviewAlarms(join(alarmsFuture, emptyAlarmsPayload())));
        return overview;
    }

    public Map<String, Object> statsSnapshot() {
        return statsSnapshot(EnumSet.allOf(StatsPart.class));
    }

    /**
     * 当前用户已授权固定摄像头的最小播放状态。复用设备统计的短缓存和单飞查询，
     * 健康事件不再触发完整 Overview，也不向浏览器暴露 Gateway 健康明细。
     */
    public List<Map<String, Object>> fixedCameraStatuses() {
        return cachedStats("devices", () -> devices(new OverviewRequestCache())).stream()
                .filter(device -> "FIXED_CAMERA".equals(firstString(device, "sourceType")))
                .map(device -> object(
                        "sourceId", device.get("robotId"),
                        "status", device.get("status"),
                        "playable", device.get("playable"),
                        "enabled", device.get("enabled"),
                        "configReady", device.get("configReady")))
                .toList();
    }

    public void invalidateDeviceStats() {
        statsCache.remove("devices:" + statsUserKey());
    }

    /**
     * 按事件类型只重算受影响部分，未包含的部分保持上一轮快照值。各部分结果带
     * 3 秒短 TTL 缓存（按用户隔离），多会话与多事件在窗口内共享一次管理端查询。
     */
    public Map<String, Object> statsSnapshot(Set<StatsPart> parts) {
        Set<StatsPart> requestedParts = parts == null || parts.isEmpty()
                ? EnumSet.allOf(StatsPart.class)
                : EnumSet.copyOf(parts);
        String key = "stats-snapshot:" + statsUserKey() + ":" + requestedParts.stream()
                .map(Enum::name).sorted().collect(Collectors.joining(","));
        if (!statsSnapshotInFlight.containsKey(key) && statsSnapshotInFlight.size() >= IN_FLIGHT_MAX_SIZE) {
            throw new IllegalStateException("大屏统计并发身份已达上限，请稍后重试");
        }
        SecurityContext callerContext = copySecurityContext(SecurityContextHolder.getContext());
        CompletableFuture<Map<String, Object>> shared = statsSnapshotInFlight.computeIfAbsent(key, ignored -> {
            CompletableFuture<Map<String, Object>> created;
            try {
                created = CompletableFuture.supplyAsync(
                        () -> loadStatsWithSecurityContext(callerContext, requestedParts), STATS_EXECUTOR);
            } catch (RejectedExecutionException exception) {
                throw new IllegalStateException("大屏统计刷新繁忙，请稍后重试", exception);
            }
            created.whenComplete((value, error) -> statsSnapshotInFlight.remove(key, created));
            return created;
        });
        return joinShared(shared, STATS_TIMEOUT_MILLIS);
    }

    private Map<String, Object> loadStatsWithSecurityContext(
            SecurityContext callerContext,
            Set<StatsPart> parts) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        Long previousDeadline = OVERVIEW_DEADLINE_NANOS.get();
        ThreadPoolExecutor previousIoExecutor = REQUEST_IO_EXECUTOR.get();
        try {
            SecurityContextHolder.setContext(callerContext);
            OVERVIEW_DEADLINE_NANOS.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STATS_TIMEOUT_MILLIS));
            REQUEST_IO_EXECUTOR.set(STATS_IO_EXECUTOR);
            return calculateStatsSnapshot(parts);
        } finally {
            SecurityContextHolder.setContext(previousContext);
            restoreOverviewDeadline(previousDeadline);
            restoreRequestIoExecutor(previousIoExecutor);
        }
    }

    private Map<String, Object> calculateStatsSnapshot(Set<StatsPart> parts) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (parts.contains(StatsPart.DEVICES)) {
            List<Map<String, Object>> devices = cachedStats("devices", () -> devices(new OverviewRequestCache()));
            stats.put("deviceStats", deviceStats(devices));
            stats.put("deviceTypeStats", deviceTypeStats(devices));
        }
        if (parts.contains(StatsPart.TASKS)) {
            List<Map<String, Object>> devices = cachedStats("devices", () -> devices(new OverviewRequestCache()));
            PanoramaTasks panoramaTasks = cachedStats("tasks", () -> taskPayload(new OverviewRequestCache()));
            List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(panoramaTasks.items(), devices);
            stats.put("patrolOverview", patrolOverview(panoramaTasks.instances(), cachedStats("mileage", this::todayMileageSummary)));
            stats.put("taskOverview", taskOverview(tasks));
            stats.put("dataQuality", object("tasks", panoramaTasks.dataQuality()));
        }
        if (parts.contains(StatsPart.ALARMS)) {
            Map<String, Object> alarms = cachedStats("alarms", this::alarmsPayload);
            stats.put("alarmStats", alarmStats(alarms));
            stats.put("alarmSummary", alarms.get("summary"));
        }
        return stats;
    }

    private <T> T cachedStats(String part, Supplier<T> supplier) {
        String key = part + ":" + statsUserKey();
        Optional<Object> cached = statsCache.get(key);
        if (cached.isPresent()) {
            return (T) cached.get();
        }
        if (!statsPartInFlight.containsKey(key) && statsPartInFlight.size() >= IN_FLIGHT_MAX_SIZE) {
            throw new IllegalStateException("大屏统计缓存并发身份已达上限，请稍后重试");
        }
        CompletableFuture<Object> created = new CompletableFuture<>();
        CompletableFuture<Object> shared = statsPartInFlight.putIfAbsent(key, created);
        if (shared == null) {
            shared = created;
            try {
                created.complete(supplier.get());
            } catch (RuntimeException exception) {
                created.completeExceptionally(exception);
            }
        }
        try {
            Object value = shared.join();
            statsCache.put(key, value);
            return (T) value;
        } finally {
            statsPartInFlight.remove(key, shared);
        }
    }

    private void cacheStatsValue(String part, Object value) {
        statsCache.put(part + ":" + statsUserKey(), value);
    }

    private String statsUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String issuer = jwt.getToken().getIssuer() == null ? "" : jwt.getToken().getIssuer().toString();
            String org = firstJwtClaim(jwt, "org_id", "orgId", "organization_id", "tenant_id");
            String permissionVersion = firstJwtClaim(jwt, "authorization_version", "permission_version", "auth_version");
            String authorities = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .sorted()
                    .collect(Collectors.joining(","));
            return String.join("|", issuer, value(jwt.getToken().getSubject(), ""), value(org, ""),
                    value(permissionVersion, ""), authorities);
        }
        return "anonymous";
    }

    private String firstJwtClaim(JwtAuthenticationToken authentication, String... names) {
        for (String name : names) {
            Object claim = authentication.getToken().getClaim(name);
            if (claim != null && !String.valueOf(claim).isBlank()) {
                return String.valueOf(claim);
            }
        }
        return null;
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
        device.remove("task");
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
                    task.remove("pathPoints");
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
        // 大屏地图使用管理端任务定义的地图 ID。边缘端上报的是 SLAM 地图 ID，
        // 两者不保证一致，未关联任务的设备不能据此错误归属到某张管理端地图。
        String taskMapId = taskMapIds.get(firstString(device, "robotId"));
        location.put("mapId", taskMapId);
        result.put("location", location);
        return result;
    }

    public Map<String, Object> mountedDeviceCount(String deviceId) {
        // 先按当前用户的设备列表授权，再补查唯一目标的组件；固定摄像头和无权设备不查询详情。
        List<Map<String, Object>> devices = cachedStats("devices", () -> devices(new OverviewRequestCache()));
        Map<String, Object> selected = devices.stream()
                .filter(device -> Objects.equals(deviceId, string(device.get("robotId"))))
                .findFirst()
                .orElse(null);
        if (selected == null || "FIXED_CAMERA".equals(firstString(selected, "sourceType", "typeCode"))) {
            return emptyMountedDeviceCount();
        }
        // 不为任意传入的无权 ID 建缓存/锁，避免攻击者用随机路径扩大内存占用。
        return cachedStats("mounted-device-count:" + deviceId, () -> mountedDeviceCountPayload(deviceId));
    }

    private Map<String, Object> mountedDeviceCountPayload(String deviceId) {
        Object count = cachedStats("management-devices", centerClient::devices).stream()
                .filter(source -> Objects.equals(deviceId, firstString(source, "serialNumber")))
                .findFirst()
                .map(source -> {
                    Map<String, Object> detail = deviceSource(source);
                    return mountedDeviceCount(detail, mountedDevices(detail, null));
                })
                .orElse(null);
        return object("robotId", deviceId, "mountedDeviceCount", count);
    }

    private Map<String, Object> emptyMountedDeviceCount() {
        return object("robotId", null, "mountedDeviceCount", null);
    }

    /** 当前地图渲染所需资源；不加载其他地图的点位。 */
    public Map<String, Object> mapResources(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("mapId is required");
        }
        return cachedStats("map-resources:" + mapId, () -> mapResourcesPayload(mapId));
    }

    private Map<String, Object> mapResourcesPayload(String mapId) {
        List<Map<String, Object>> points = centerClient.mapPoints(mapId);
        List<Map<String, Object>> fixedCameras = centerClient.fixedCameras(mapId);
        // 地图渲染资源是地图与顶层 devices[] 的关联入口。只返回 ID，避免重复下发设备详情。
        // 现场设备常不在实时定位中携带管理端 mapId，必须复用任务定义补齐后的地图归属，
        // 否则任务已绑定地图的设备会被错误地遗漏。
        List<Map<String, Object>> devices = mapAssociatedDevices();
        return object(
                "serverTime", now(),
                "mapId", mapId,
                "points", overviewPoints(points),
                "deviceIds", deviceIdsForMap(mapId, devices),
                "fixedCamares", fixedCameras);
    }

    private List<Map<String, Object>> mapAssociatedDevices() {
        List<Map<String, Object>> devices = cachedStats("devices", () -> devices(new OverviewRequestCache()));
        // overview 的 tasks 是首屏摘要，不读取工作流定义；场景关联需要管理端地图 ID，
        // 因此仅在用户请求地图渲染资源时按需补齐，且与首屏摘要缓存隔离。这里刻意不复用
        // taskPayload：场景关联只需要任务、设备与地图，不能额外读取路径点和任务回放。
        List<Map<String, Object>> tasks = cachedStats("map-association-tasks", this::mapAssociationTasks);
        return withTaskLocationMapIds(devices, tasks);
    }

    private List<Map<String, Object>> mapAssociationTasks() {
        TaskDataQuality quality = new TaskDataQuality();
        List<Map<String, Object>> taskPlans = joinTask(
                async(centerClient::taskWorkflowPlans), List.of(), quality, "TASK_PLANS_UNAVAILABLE");
        List<CompletableFuture<Map<String, Object>>> futures = taskPlans.stream()
                .map(task -> async(() -> mapAssociationTask(task, quality)))
                .toList();
        return futures.stream()
                .map(future -> joinTask(future, null, quality, "TASK_MAP_ASSOCIATION_UNAVAILABLE"))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> mapAssociationTask(Map<String, Object> task, TaskDataQuality quality) {
        Object mapId = firstValue(task, "mapId", "mapID");
        String workflowDefinitionId = firstString(task, "workflowDefinitionId", "definitionId");
        if ((mapId == null || string(mapId).isBlank())
                && workflowDefinitionId != null && !workflowDefinitionId.isBlank()) {
            Optional<Map<String, Object>> definition = centerClient.taskWorkflowDefinition(workflowDefinitionId);
            if (definition.isPresent()) {
                mapId = firstValue(definition.get(), "mapId", "mapID");
            } else {
                quality.invalidReference("WORKFLOW_DEFINITION_NOT_FOUND", workflowDefinitionId);
            }
        }
        return object(
                "mapId", mapId,
                "equipmentList", equipmentList(task, Map.of(), Map.of(), List.of()));
    }

    /** 当前地图关联任务的路径数据；不加载任务回放或设备任务明细。 */
    public Map<String, Object> mapTaskRoutes(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("mapId is required");
        }
        return cachedStats("map-task-routes:" + mapId, () -> mapTaskRoutesPayload(mapId));
    }

    private Map<String, Object> mapTaskRoutesPayload(String mapId) {
        OverviewRequestCache cache = new OverviewRequestCache();
        TaskDataQuality quality = new TaskDataQuality();
        List<Map<String, Object>> taskPlans = joinTask(
                async(centerClient::taskWorkflowPlans), List.of(), quality, "TASK_PLANS_UNAVAILABLE");
        TaskRouteResolver routeResolver = new TaskRouteResolver(taskPlans, cache, quality);
        for (int index = 0; index < taskPlans.size(); index++) {
            routeResolver.prefetch(taskPlans.get(index), index);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < taskPlans.size(); index++) {
            Map<String, Object> task = taskPlans.get(index);
            TaskRouteData route = routeResolver.resolve(task, index);
            if (Objects.equals(mapId, string(route.mapId()))) {
                items.add(object(
                        "taskId", firstValue(task, "id", "taskId"),
                        "workflowInstanceId", planWorkflowInstanceId(task),
                        "mapId", route.mapId(),
                        "pathPoints", overviewPoints(route.pathPoints())));
            }
        }
        return object(
                "serverTime", now(),
                "mapId", mapId,
                "items", items,
                "dataQuality", object("tasks", quality.snapshot()));
    }

    /** 任务详情仅在用户打开任务时加载，避免首屏预取回放和设备任务明细。 */
    public Map<String, Object> taskDetail(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        OverviewRequestCache cache = new OverviewRequestCache();
        PanoramaTasks panoramaTasks = taskPayload(cache);
        return panoramaTasks.items().stream()
                .filter(task -> Objects.equals(taskId, string(task.get("taskId"))))
                .findFirst()
                .map(task -> object(
                        "serverTime", now(),
                        "task", task,
                        "dataQuality", object("tasks", panoramaTasks.dataQuality())))
                .orElseGet(() -> object(
                        "serverTime", now(),
                        "task", null,
                "dataQuality", object("tasks", panoramaTasks.dataQuality())));
    }

    /**
     * 实时监控任务卡展开时按需读取的固定摄像头视频源。这里不创建视频会话，
     * 不返回 RTSP 地址或凭据；浏览器选择后仍走 Control 的固定摄像头会话接口。
     */
    public Map<String, Object> taskFixedCameras(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        Map<String, Map<String, Object>> itemsBySourceId = new LinkedHashMap<>();
        for (Map<String, Object> source : centerClient.taskWorkflowPlanFixedCameras(taskId)) {
            String cameraId = firstString(source, "cameraId", "id");
            if (cameraId == null || cameraId.isBlank()) {
                continue;
            }
            itemsBySourceId.putIfAbsent(cameraId, object(
                    "cameraId", cameraId,
                    "name", firstString(source, "cameraName", "name"),
                    "sourceType", "FIXED_CAMERA",
                    "sourceId", cameraId,
                    "defaultQuality", firstString(source, "subStreamUrl") == null ? "main" : "sub"));
        }
        return object(
                "serverTime", now(),
                "taskId", taskId,
                "items", List.copyOf(itemsBySourceId.values()));
    }

    public Map<String, Object> tasks() {
        OverviewRequestCache cache = new OverviewRequestCache();
        CompletableFuture<PanoramaTasks> tasksFuture = async(() -> taskPayload(cache));
        CompletableFuture<List<Map<String, Object>>> devicesFuture = async(() -> devices(cache));
        PanoramaTasks panoramaTasks = join(tasksFuture, unavailableTasks("TASK_AGGREGATION_FAILED"));
        List<Map<String, Object>> tasks = withEquipmentOnlineStatuses(
                panoramaTasks.items(),
                joinRequired(devicesFuture));
        return object(
                "serverTime", now(),
                "total", tasks.size(),
                "items", tasks,
                "dataQuality", object("tasks", panoramaTasks.dataQuality()));
    }

    /** WebSocket 任务事件只读取列表摘要，避免状态变化时加载回放、路径和设备任务明细。 */
    public Map<String, Object> taskEventSnapshot() {
        ThreadPoolExecutor previousExecutor = REQUEST_IO_EXECUTOR.get();
        REQUEST_IO_EXECUTOR.set(TASK_EVENT_IO_EXECUTOR);
        try {
            PanoramaTasks panoramaTasks = taskSummaries();
            if (Boolean.TRUE.equals(panoramaTasks.dataQuality().get("degraded"))) {
                throw new IllegalStateException("全景地图任务事件快照不完整");
            }
            return object(
                    "items", panoramaTasks.items(),
                    "convergencePending", panoramaTasks.convergencePending());
        } finally {
            restoreRequestIoExecutor(previousExecutor);
        }
    }

    public Map<String, Object> alarms() {
        return object(
                "serverTime", now(),
                "alarms", alarmsPayload());
    }

    public Map<String, Object> alarmEventSnapshot() {
        CompletableFuture<PanoramaCenterClient.AlarmPage> highFuture = alarmPageFuture("CRITICAL", null, null, ALARM_PAGE_SIZE);
        CompletableFuture<PanoramaCenterClient.AlarmPage> mediumFuture = alarmPageFuture("WARN", null, null, ALARM_PAGE_SIZE);
        CompletableFuture<PanoramaCenterClient.AlarmPage> lowFuture = alarmPageFuture("INFO", null, null, ALARM_PAGE_SIZE);
        return alarmListPayload(
                joinRequired(highFuture),
                joinRequired(mediumFuture),
                joinRequired(lowFuture));
    }

    public Map<String, Object> alarmPage(
            String level,
            int pageNum,
            int pageSize,
            String occurredFrom,
            String occurredTo) {
        PanoramaCenterClient.AlarmPage page = centerClient.alarmPage(
                "NEW",
                managementSeverity(level),
                occurredFrom,
                occurredTo,
                pageNum,
                pageSize);
        return object(
                "serverTime", now(),
                "total", page.total(),
                "pageNum", page.pageNum(),
                "pageSize", page.pageSize(),
                "items", page.records().stream().map(alarm -> alarmItem(alarm, null)).toList());
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

    public Map<String, Object> handleAlarm(String alarmId, Map<String, Object> request) {
        if (alarmId == null || alarmId.isBlank()) {
            throw new IllegalArgumentException("alarmId is required");
        }
        AlarmDisposalStatus disposalStatus = AlarmDisposalStatus.from(string(request == null ? null : request.get("disposalStatus")));
        String handleResult = string(request == null ? null : request.get("handleResult"));
        boolean success = centerClient.handleAlarm(alarmId, disposalStatus.managementAction, handleResult);
        return disposalResponse(alarmId, disposalStatus, success);
    }

    public Map<String, Object> handleWorkflowAlarm(String alarmId, Map<String, Object> request) {
        if (alarmId == null || alarmId.isBlank()) {
            throw new IllegalArgumentException("alarmId is required");
        }
        AlarmDisposalStatus disposalStatus = AlarmDisposalStatus.from(string(request == null ? null : request.get("disposalStatus")));
        String handleResult = string(request == null ? null : request.get("handleResult"));
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
        CompletableFuture<List<Map<String, Object>>> managementDevicesFuture = async(
                () -> cachedStats("management-devices", centerClient::devices));
        CompletableFuture<List<Map<String, Object>>> registeredRobotsFuture = async(centerClient::registeredRobots);
        CompletableFuture<List<Map<String, Object>>> fixedCamerasFuture = cache.allFixedCamerasFuture();
        CompletableFuture<Map<String, Object>> fixedCameraHealthFuture = async(centerClient::fixedCameraHealth);
        CompletableFuture<List<Map<String, Object>>> deviceTypeOptionsFuture = async(centerClient::deviceTypeOptions);
        List<Map<String, Object>> managementDevices = joinRequired(managementDevicesFuture);
        List<Map<String, Object>> registeredRobots = join(registeredRobotsFuture, List.of());
        Map<String, Map<String, Object>> registeredRobotsById = registeredRobots.stream()
                .filter(this::hasDeviceId)
                .collect(Collectors.toMap(
                        robot -> firstString(robot, "robotId", "serialNumber"),
                        Function.identity(),
                        (left, right) -> right));
        CompletableFuture<Map<String, Map<String, Object>>> statusBySerialFuture = async(() -> statusBySerial(managementDevices));
        List<Map<String, Object>> validManagementDevices = managementDevices.stream()
                .filter(this::hasDeviceId)
                .toList();
        Map<String, Map<String, Object>> statusBySerial = join(statusBySerialFuture, Map.of());
        Map<String, String> deviceTypeNames = deviceTypeNames(join(deviceTypeOptionsFuture, List.of()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < validManagementDevices.size(); index++) {
            Map<String, Object> managementDevice = validManagementDevices.get(index);
            String robotId = firstString(managementDevice, "serialNumber");
            Map<String, Object> realtimeStatus = statusBySerial.getOrDefault(robotId, Map.of());
            Map<String, Object> registeredRobot = registeredRobotsById.getOrDefault(robotId, Map.of());
            Map<String, Object> device = device(managementDevice, realtimeStatus, registeredRobot, deviceTypeNames);
            result.add(device);
        }
        Map<String, Object> fixedCameraHealthResponse = join(fixedCameraHealthFuture, Map.of());
        Map<String, Map<String, Object>> fixedCameraHealth = list(fixedCameraHealthResponse.get("records")).stream()
                .filter(item -> firstString(item, "cameraId") != null)
                .collect(Collectors.toMap(
                        item -> firstString(item, "cameraId"),
                        Function.identity(),
                        (left, right) -> right));
        List<Map<String, Object>> fixedCameras = join(fixedCamerasFuture, List.of());
        fixedCameras.stream()
                .filter(camera -> firstValue(camera, "cameraId", "id") != null)
                .map(camera -> fixedCameraDevice(camera, fixedCameraHealth.getOrDefault(
                        firstString(camera, "cameraId", "id"), Map.of())))
                .forEach(result::add);
        return result;
    }

    private Map<String, Object> deviceSource(Map<String, Object> listDevice) {
        String id = firstString(listDevice, "id");
        if (id == null) {
            return listDevice;
        }
        return centerClient.device(id)
                .map(detail -> {
                    Map<String, Object> source = mergeDevice(listDevice, map(detail.get("device")));
                    if (detail.get("components") instanceof List<?>) source.put("components", detail.get("components"));
                    return source;
                })
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
            Map<String, Object> registeredRobot,
            Map<String, String> deviceTypeNames) {
        Map<String, Object> basic = map(path(realtimeStatus, "status", "basic"));
        Map<String, Object> localization = map(path(realtimeStatus, "status", "localization"));
        Map<String, Object> task = map(path(realtimeStatus, "status", "task"));

        Object robotId = firstValue(source, "serialNumber", "robotId", "id");
        Object alarmLevel = alarmLevel(basic);
        // 在线及电量、速度、模式只认本项目 Control 注册表。管理端实时状态仍用于定位等
        // 业务字段，但其 Redis 过期语义不能作为大屏在线状态源，否则首屏与 WebSocket 会分叉。
        String status = onlineStatus(firstString(registeredRobot, "status"));
        String statusChangedAt = value(firstString(registeredRobot, "statusChangedAt"), statusVersionNow());
        Object fault = switch (status) {
            case "fault" -> Boolean.TRUE;
            case "online" -> Boolean.FALSE;
            default -> null;
        };
        List<Map<String, Object>> mountedDevices = mountedDevices(source, status);
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
                "statusChangedAt", statusChangedAt,
                "battery", number(registeredRobot.get("battery")),
                "runtimeUpdatedAt", value(firstString(registeredRobot, "runtimeUpdatedAt"), statusVersionNow()),
                "lastHeartbeatAt", formatTime(firstString(realtimeStatus, "lastSeenAt", "receivedAt", "reportedAt")),
                "cameras", cameras(source, registeredRobot, string(robotId)),
                "mountedDevices", mountedDevices,
                "stateSeq", number(registeredRobot.get("stateSeq")),
                "fault", fault,
                "alarmLevel", alarmLevel,
                "controlMode", normalizeControlMode(firstString(registeredRobot, "controlMode")),
                "controlModeName", controlModeName(normalizeControlMode(firstString(registeredRobot, "controlMode"))),
                "mountedDeviceCount", mountedDeviceCount(source, mountedDevices),
                "speed", number(registeredRobot.get("speed")),
                "location", location(localization, realtimeStatus),
                "mapDisplay", mapDisplay(name, status, alarmLevel),
                "task", deviceTasks(task));
    }

    private List<Map<String, Object>> normalizedRegisteredRobotCameras(Map<String, Object> source) {
        return list(source.get("cameras")).stream()
                .map(camera -> object(
                        "cameraId", firstValue(camera, "cameraId", "id", "deviceId"),
                        "deviceId", firstValue(camera, "deviceId", "cameraId", "id"),
                        "groupType", firstValue(camera, "groupType"),
                        "name", firstString(camera, "name", "cameraName"),
                        "quality", value(firstString(camera, "quality"), "sub")))
                .filter(camera -> camera.get("cameraId") != null || camera.get("deviceId") != null)
                .toList();
    }

    private Map<String, Object> fixedCameraDevice(Map<String, Object> source, Map<String, Object> health) {
        String cameraId = firstString(source, "cameraId", "id");
        boolean enabled = booleanValue(source.get("enabled"));
        String defaultQuality = firstString(source, "subStreamUrl") == null ? "main" : "sub";
        boolean protocolReady = firstString(source, "protocolType") == null
                || "RTSP".equalsIgnoreCase(firstString(source, "protocolType"));
        boolean configReady = protocolReady
                && (firstString(source, "mainStreamUrl") != null || firstString(source, "subStreamUrl") != null);
        Map<String, Object> gatewayHealth = map(health.get("gatewayHealth"));
        Map<String, Object> streamHealth = map(health.get("streamHealth"));
        String status = fixedCameraStatus(enabled, configReady, gatewayHealth, streamHealth);
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
                "enabled", enabled,
                "configStatus", configReady ? "READY" : "INVALID",
                "configReady", configReady,
                "gatewayId", firstValue(health, "gatewayId"),
                "gatewayHealth", normalizedHealth(gatewayHealth, "ONLINE", "OFFLINE", "UNKNOWN"),
                "streamHealth", normalizedHealth(streamHealth, "AVAILABLE", "UNAVAILABLE", "UNKNOWN"),
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
                "playable", enabled && configReady,
                "showControlCenter", false,
                "showController", false);
    }

    private String fixedCameraStatus(
            boolean enabled,
            boolean configReady,
            Map<String, Object> gatewayHealth,
            Map<String, Object> streamHealth) {
        if (!enabled) {
            return "offline";
        }
        if (!configReady) {
            return "offline";
        }
        String gateway = firstString(gatewayHealth, "status");
        if ("OFFLINE".equalsIgnoreCase(gateway)) {
            return "offline";
        }
        if (!"ONLINE".equalsIgnoreCase(gateway)) {
            return "offline";
        }
        String stream = firstString(streamHealth, "status");
        if ("AVAILABLE".equalsIgnoreCase(stream)) {
            return "online";
        }
        if ("UNAVAILABLE".equalsIgnoreCase(stream)) {
            return "offline";
        }
        return "offline";
    }

    private Map<String, Object> normalizedHealth(Map<String, Object> source, String... allowed) {
        String status = firstString(source, "status");
        boolean valid = status != null && java.util.Arrays.stream(allowed).anyMatch(value -> value.equalsIgnoreCase(status));
        return object(
                "status", valid ? status.toUpperCase(Locale.ROOT) : "UNKNOWN",
                "observedAt", source.get("observedAt"),
                "reasonCode", valid ? source.get("reasonCode") : "STATUS_MISSING");
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
        return source.get("components") instanceof List<?> ? mountedDevices.size() : null;
    }

    private List<Map<String, Object>> mountedDevices(Map<String, Object> source, String robotStatus) {
        List<Map<String, Object>> components = list(source.get("components"));
        if (components.isEmpty()) {
            return List.of();
        }
        return components.stream()
                // 口径为非本体组件记录数；不从能力数量或媒体通道数量推算上装数量。
                .filter(component -> !"BODY".equalsIgnoreCase(firstString(component, "componentType")))
                .map(component -> object(
                        "deviceId", firstValue(component, "code", "deviceId", "id"),
                        "name", firstString(component, "name", "componentName"),
                        "type", componentType(component),
                        "status", robotStatus))
                .toList();
    }

    private List<Map<String, Object>> cameras(
            Map<String, Object> source,
            Map<String, Object> registeredRobot,
            String robotId) {
        List<Map<String, Object>> realtimeCameras = normalizedRegisteredRobotCameras(registeredRobot);
        if (!realtimeCameras.isEmpty()) {
            return realtimeCameras;
        }
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
        TaskDataQuality quality = new TaskDataQuality();
        CompletableFuture<List<Map<String, Object>>> taskPlansFuture = async(centerClient::taskWorkflowPlans);
        CompletableFuture<List<Map<String, Object>>> taskInstancesFuture = async(centerClient::taskWorkflowInstances);
        List<Map<String, Object>> taskPlans = joinTask(
                taskPlansFuture, List.of(), quality, "TASK_PLANS_UNAVAILABLE");
        List<Map<String, Object>> taskInstances = joinTask(
                taskInstancesFuture, List.of(), quality, "TASK_INSTANCES_UNAVAILABLE");
        if (taskPlans.isEmpty()) {
            return new PanoramaTasks(List.of(), taskInstances, quality.snapshot(), false);
        }
        TaskInstanceResolver taskInstanceResolver = new TaskInstanceResolver(taskInstances, quality);
        TaskRouteResolver routeResolver = new TaskRouteResolver(taskPlans, cache, quality);
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
                .map(future -> joinTask(future, null, quality, "TASK_ITEM_UNAVAILABLE"))
                .filter(Objects::nonNull)
                .toList();
        return new PanoramaTasks(result, taskInstances, quality.snapshot(), hasPreparingPlan(taskPlans));
    }

    /**
     * 首屏只读取任务计划和实例列表，输出页面列表、统计与设备关联所需的摘要；不触发定义、回放、
     * 设备任务、路径点等按需数据。
     */
    private PanoramaTasks taskSummaries() {
        TaskDataQuality quality = new TaskDataQuality();
        CompletableFuture<List<Map<String, Object>>> taskPlansFuture = async(centerClient::taskWorkflowPlans);
        CompletableFuture<List<Map<String, Object>>> taskInstancesFuture = async(centerClient::taskWorkflowInstances);
        List<Map<String, Object>> taskPlans = joinTask(
                taskPlansFuture, List.of(), quality, "TASK_PLANS_UNAVAILABLE");
        List<Map<String, Object>> taskInstances = joinTask(
                taskInstancesFuture, List.of(), quality, "TASK_INSTANCES_UNAVAILABLE");
        Map<String, Map<String, Object>> instancesById = taskInstances.stream()
                .filter(item -> firstString(item, "id", "workflowInstanceId") != null)
                .collect(Collectors.toMap(
                        item -> firstString(item, "id", "workflowInstanceId"),
                        Function.identity(),
                        (left, right) -> right));
        List<Map<String, Object>> items = taskPlans.stream()
                .map(plan -> taskSummary(plan, instancesById.get(string(planWorkflowInstanceId(plan)))))
                .toList();
        return new PanoramaTasks(items, taskInstances, quality.snapshot(), hasPreparingPlan(taskPlans));
    }

    private Map<String, Object> taskSummary(Map<String, Object> source, Map<String, Object> instance) {
        Map<String, Object> safeInstance = instance == null ? Map.of() : instance;
        String rawStatus = taskPlanStatus(source, safeInstance);
        String startTime = formatTime(value(
                firstString(safeInstance, "startedAt"),
                firstString(source, "startedAt", "lastStartedAt", "startTime")));
        String endTime = formatTime(value(
                firstString(safeInstance, "completedAt"),
                firstString(source, "completedAt", "lastCompletedAt", "endTime")));
        return object(
                "taskId", firstValue(source, "id", "taskId"),
                "workflowInstanceId", planWorkflowInstanceId(source),
                "name", firstString(source, "planName", "workflowName", "name"),
                "executionMode", firstValue(source, "executionMode"),
                "expectedDurationSeconds", firstValue(source, "expectedDurationSeconds"),
                "availableLifecycleActions", firstValue(source, "availableLifecycleActions"),
                "status", taskStatusCode(rawStatus),
                "statusName", taskStatusName(rawStatus),
                "startTime", startTime,
                "endTime", endTime,
                "timeRange", timeRange(startTime, endTime, null),
                "equipmentList", equipmentList(source, safeInstance, Map.of(), List.of()),
                "mapId", firstValue(source, "mapId", "mapID"));
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
                "availableLifecycleActions", firstValue(source, "availableLifecycleActions"),
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
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        String occurredFrom = now.toLocalDate().atStartOfDay().format(DATE_TIME_FORMATTER);
        String occurredTo = now.format(DATE_TIME_FORMATTER);
        CompletableFuture<PanoramaCenterClient.AlarmPage> highFuture = alarmPageFuture("CRITICAL", null, null, ALARM_PAGE_SIZE);
        CompletableFuture<PanoramaCenterClient.AlarmPage> mediumFuture = alarmPageFuture("WARN", null, null, ALARM_PAGE_SIZE);
        CompletableFuture<PanoramaCenterClient.AlarmPage> lowFuture = alarmPageFuture("INFO", null, null, ALARM_PAGE_SIZE);
        CompletableFuture<PanoramaCenterClient.AlarmPage> todayFuture = async(
                () -> centerClient.alarmPage(null, null, occurredFrom, occurredTo, 1, 1));
        CompletableFuture<PanoramaCenterClient.AlarmPage> handledFuture = async(
                () -> centerClient.alarmPage("HANDLED", null, occurredFrom, occurredTo, 1, 1));
        CompletableFuture<PanoramaCenterClient.AlarmPage> falseAlarmFuture = async(
                () -> centerClient.alarmPage("FALSE_ALARM", null, occurredFrom, occurredTo, 1, 1));
        PanoramaCenterClient.AlarmPage highPage = join(highFuture, emptyAlarmPage(ALARM_PAGE_SIZE));
        PanoramaCenterClient.AlarmPage mediumPage = join(mediumFuture, emptyAlarmPage(ALARM_PAGE_SIZE));
        PanoramaCenterClient.AlarmPage lowPage = join(lowFuture, emptyAlarmPage(ALARM_PAGE_SIZE));
        Map<String, Object> payload = new LinkedHashMap<>(alarmListPayload(highPage, mediumPage, lowPage));
        long handled = join(handledFuture, emptyAlarmPage(1)).total()
                + join(falseAlarmFuture, emptyAlarmPage(1)).total();
        payload.put("summary", alarmSummary(join(todayFuture, emptyAlarmPage(1)).total(), handled));
        return payload;
    }

    private Map<String, Object> alarmListPayload(
            PanoramaCenterClient.AlarmPage highPage,
            PanoramaCenterClient.AlarmPage mediumPage,
            PanoramaCenterClient.AlarmPage lowPage) {
        long total = highPage.total() + mediumPage.total() + lowPage.total();
        List<Map<String, Object>> latest = java.util.stream.Stream.of(highPage, mediumPage, lowPage)
                .flatMap(page -> page.records().stream())
                .map(alarm -> alarmItem(alarm, null))
                .sorted((left, right) -> Objects.toString(right.get("eventTime"), "")
                        .compareTo(Objects.toString(left.get("eventTime"), "")))
                .limit(ALARM_PAGE_SIZE)
                .toList();
        return object(
                "total", total,
                "latest", alarmGroup(latest, total, 1, ALARM_PAGE_SIZE),
                "high", alarmGroup(highPage),
                "medium", alarmGroup(mediumPage),
                "low", alarmGroup(lowPage));
    }

    private CompletableFuture<PanoramaCenterClient.AlarmPage> alarmPageFuture(
            String severity,
            String occurredFrom,
            String occurredTo,
            int pageSize) {
        return async(() -> centerClient.alarmPage("NEW", severity, occurredFrom, occurredTo, 1, pageSize));
    }

    private PanoramaCenterClient.AlarmPage emptyAlarmPage(int pageSize) {
        return new PanoramaCenterClient.AlarmPage(List.of(), 0, 1, pageSize);
    }

    private Map<String, Object> alarmItem(Map<String, Object> source, TaskInstanceResolver taskInstanceResolver) {
        Object taskId = firstValue(source, "taskInstanceId", "taskId");
        Map<String, Object> taskInstance = taskInstanceResolver == null ? Map.of() : taskInstanceResolver.instance(taskId);
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
                "workflowActionable", true,
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

    private Map<String, Object> alarmSummary(long total, long handled) {
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
                "high", alarmTotal(alarms, "high"),
                "medium", alarmTotal(alarms, "medium"),
                "low", alarmTotal(alarms, "low"));
    }

    private long alarmTotal(Map<String, Object> alarms, String level) {
        Number total = number(map(alarms.get(level)).get("total"));
        return total == null ? 0 : total.longValue();
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
        long fault = devices.stream().filter(device -> "fault".equals(device.get("status"))).count();
        long offline = devices.size() - online - fault;
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
                    long online = items.stream().filter(item -> "online".equals(item.get("status"))).count();
                    long fault = items.stream().filter(item -> "fault".equals(item.get("status"))).count();
                    long offline = items.size() - online - fault;
                    return object("type", entry.getKey(), "name", name, "count", items.size(), "fault", fault,
                            "offline", offline);
                })
                .toList();
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
                "mileageUnit", mileageToday == null ? null : "KM",
                "mileageHasData", Boolean.TRUE.equals(mileageSummary.get("hasData")));
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
                "summary", alarmSummary(0, 0),
                "latest", alarmGroup(List.of(), 0, 1, ALARM_PAGE_SIZE),
                "high", alarmGroup(List.of(), 0, 1, ALARM_PAGE_SIZE),
                "medium", alarmGroup(List.of(), 0, 1, ALARM_PAGE_SIZE),
                "low", alarmGroup(List.of(), 0, 1, ALARM_PAGE_SIZE));
    }

    private static java.util.concurrent.ThreadFactory namedDaemonThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix);
            thread.setDaemon(true);
            return thread;
        };
    }

    private <T> CompletableFuture<T> asyncOverview(Supplier<T> supplier) {
        return async(supplier);
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier) {
        ThreadPoolExecutor executor = REQUEST_IO_EXECUTOR.get();
        return async(supplier, executor == null ? IO_EXECUTOR : executor);
    }

    private <T> CompletableFuture<T> async(Supplier<T> supplier, ThreadPoolExecutor executor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long deadlineNanos = OVERVIEW_DEADLINE_NANOS.get();
        ThreadPoolExecutor requestIoExecutor = REQUEST_IO_EXECUTOR.get();
        try {
            return CompletableFuture.supplyAsync(() -> {
                SecurityContext previousContext = SecurityContextHolder.getContext();
                Long previousDeadline = OVERVIEW_DEADLINE_NANOS.get();
                ThreadPoolExecutor previousIoExecutor = REQUEST_IO_EXECUTOR.get();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                try {
                    SecurityContextHolder.setContext(context);
                    if (deadlineNanos == null) {
                        OVERVIEW_DEADLINE_NANOS.remove();
                    } else {
                        OVERVIEW_DEADLINE_NANOS.set(deadlineNanos);
                    }
                    if (requestIoExecutor == null) {
                        REQUEST_IO_EXECUTOR.remove();
                    } else {
                        REQUEST_IO_EXECUTOR.set(requestIoExecutor);
                    }
                    requireRequestTimeRemaining();
                    return supplier.get();
                } finally {
                    SecurityContextHolder.setContext(previousContext);
                    restoreOverviewDeadline(previousDeadline);
                    restoreRequestIoExecutor(previousIoExecutor);
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new PanoramaCenterClient.TaskSourceException(
                    executor == OVERVIEW_EXECUTOR ? "OVERVIEW_EXECUTOR_SATURATED" : "TASK_EXECUTOR_SATURATED",
                    executor == OVERVIEW_EXECUTOR ? "全景总览编排执行器已饱和" : "全景聚合下游执行器已饱和",
                    exception));
        }
    }

    private <T> T join(CompletableFuture<T> future, T fallback) {
        try {
            T value = waitFor(future);
            return value == null ? fallback : value;
        } catch (CompletionException | TimeoutException exception) {
            if (exception instanceof TimeoutException) {
                future.cancel(true);
            }
            return fallback;
        }
    }

    private <T> T joinTask(
            CompletableFuture<T> future,
            T fallback,
            TaskDataQuality quality,
            String defaultReasonCode) {
        try {
            T value = waitFor(future);
            return value == null ? fallback : value;
        } catch (TimeoutException exception) {
            future.cancel(true);
            quality.unavailable(exception, "OVERVIEW_TIMEOUT");
            return fallback;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof org.springframework.web.server.ResponseStatusException responseStatus
                    && (responseStatus.getStatusCode().value() == 401
                    || responseStatus.getStatusCode().value() == 403)) {
                throw responseStatus;
            }
            quality.unavailable(cause, defaultReasonCode);
            return fallback;
        }
    }

    private <T> T joinRequired(CompletableFuture<T> future) {
        try {
            T value = waitFor(future);
            if (value == null) {
                throw new IllegalStateException("必需的中心端查询返回空响应");
            }
            return value;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "全景总览查询超时",
                    exception);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("必需的中心端查询失败", cause);
        }
    }

    private <T> T waitFor(CompletableFuture<T> future) throws TimeoutException {
        Long deadlineNanos = OVERVIEW_DEADLINE_NANOS.get();
        if (deadlineNanos == null) {
            return future.join();
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new TimeoutException("全景总览查询超过总时限");
        }
        try {
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new CompletionException(exception.getCause());
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new TimeoutException("全景总览查询超过总时限");
        }
    }

    private static void restoreOverviewDeadline(Long previousDeadline) {
        if (previousDeadline == null) {
            OVERVIEW_DEADLINE_NANOS.remove();
        } else {
            OVERVIEW_DEADLINE_NANOS.set(previousDeadline);
        }
    }

    static void requireRequestTimeRemaining() {
        Long deadlineNanos = OVERVIEW_DEADLINE_NANOS.get();
        if (deadlineNanos != null && System.nanoTime() >= deadlineNanos) {
            throw new PanoramaCenterClient.TaskSourceException(
                    "PANORAMA_DEADLINE_EXCEEDED",
                    "全景聚合已到达总截止时间");
        }
    }

    private static void restoreRequestIoExecutor(ThreadPoolExecutor previousExecutor) {
        if (previousExecutor == null) {
            REQUEST_IO_EXECUTOR.remove();
        } else {
            REQUEST_IO_EXECUTOR.set(previousExecutor);
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

    private Map<String, Object> alarmGroup(PanoramaCenterClient.AlarmPage page) {
        List<Map<String, Object>> items = page.records().stream()
                .map(alarm -> alarmItem(alarm, null))
                .toList();
        return alarmGroup(items, page.total(), page.pageNum(), page.pageSize());
    }

    private Map<String, Object> alarmGroup(List<Map<String, Object>> items, long total, int pageNum, int pageSize) {
        return object("total", total, "pageNum", pageNum, "pageSize", pageSize, "items", items);
    }

    private String managementSeverity(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "HIGH", "CRITICAL" -> "CRITICAL";
            case "MEDIUM", "WARN" -> "WARN";
            case "LOW", "INFO" -> "INFO";
            default -> throw new IllegalArgumentException("unsupported alarm level");
        };
    }

    private final class OverviewRequestCache {

        private final Map<String, CompletableFuture<List<Map<String, Object>>>> mapPointsByMapId = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<List<Map<String, Object>>>> pathPointsByPathId = new ConcurrentHashMap<>();
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
        private final TaskDataQuality quality;

        private TaskInstanceResolver(List<Map<String, Object>> taskInstances, TaskDataQuality quality) {
            this.quality = quality;
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
            Map<String, Object> loaded = joinTask(instanceFuturesById.computeIfAbsent(id,
                    value -> async(() -> loadInstance(value))), Map.of(), quality, "WORKFLOW_INSTANCE_UNAVAILABLE");
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
            return joinTask(replaysById.computeIfAbsent(id,
                    value -> async(() -> centerClient.taskWorkflowReplay(value).orElse(Map.of()))),
                    Map.of(), quality, "TASK_REPLAY_UNAVAILABLE");
        }

        private List<Map<String, Object>> deviceTaskInstances(Object workflowInstanceId) {
            String id = key(workflowInstanceId);
            if (id == null) {
                return List.of();
            }
            return joinTask(deviceTasksByWorkflowInstanceId.computeIfAbsent(id,
                    value -> async(() -> centerClient.deviceTaskInstances(value))),
                    List.of(), quality, "DEVICE_TASKS_UNAVAILABLE");
        }

        private Map<String, Object> loadInstance(String workflowInstanceId) {
            Optional<Map<String, Object>> instance = centerClient.taskWorkflowInstance(workflowInstanceId);
            if (instance.isEmpty()) {
                quality.invalidReference("WORKFLOW_INSTANCE_NOT_FOUND", workflowInstanceId);
                return Map.of();
            }
            return unwrapInstance(instance.get());
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
        private final TaskDataQuality quality;

        private TaskRouteResolver(
                List<Map<String, Object>> plans,
                OverviewRequestCache cache,
                TaskDataQuality quality) {
            this.plans = plans == null ? List.of() : plans;
            this.cache = cache;
            this.quality = quality;
            this.plansById = indexPlans("id", "planId", "workflowPlanId", "taskWorkflowPlanId", "code");
            this.plansByName = indexPlans("planName", "workflowName", "name");
        }

        private TaskRouteData resolve(Map<String, Object> source, int index) {
            String workflowDefinitionId = workflowDefinitionId(source, index);
            if (workflowDefinitionId == null || workflowDefinitionId.isBlank()) {
                return TaskRouteData.empty();
            }
            return joinTask(routesByDefinitionId.computeIfAbsent(workflowDefinitionId,
                    value -> async(() -> routeData(value))),
                    TaskRouteData.empty(), quality, "TASK_ROUTE_UNAVAILABLE");
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
            Optional<Map<String, Object>> definitionResult = centerClient.taskWorkflowDefinition(workflowDefinitionId);
            if (definitionResult.isEmpty()) {
                quality.invalidReference("WORKFLOW_DEFINITION_NOT_FOUND", workflowDefinitionId);
                return TaskRouteData.empty();
            }
            Map<String, Object> definition = definitionResult.get();
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
            return "offline";
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "ONLINE", "ON_LINE" -> "online";
            case "FAULT" -> "fault";
            case "OFFLINE", "OFF_LINE" -> "offline";
            default -> "offline";
        };
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
            case "WAITING", "PENDING" -> "waiting";
            case "PREPARING", "RUNNING" -> "running";
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

    private boolean hasPreparingPlan(List<Map<String, Object>> taskPlans) {
        return taskPlans.stream().anyMatch(plan ->
                "PREPARING".equalsIgnoreCase(firstString(plan, "activeWorkflowInstanceStatus")));
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

    private String statusVersionNow() {
        return OffsetDateTime.now(CHINA_ZONE).toString();
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

    private String normalizeControlMode(String controlMode) {
        if (controlMode == null || controlMode.isBlank()) {
            return null;
        }
        if ("导航模式".equals(controlMode)) return controlMode;
        return "手动模式".equals(controlMode) || "常规模式".equals(controlMode) ? "手动模式" : null;
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

    private PanoramaTasks unavailableTasks(String reasonCode) {
        TaskDataQuality quality = new TaskDataQuality();
        quality.unavailable(null, reasonCode);
        return new PanoramaTasks(List.of(), List.of(), quality.snapshot(), false);
    }

    private final class TaskDataQuality {

        private static final int MAX_REPORTED_INVALID_REFERENCES = 20;
        private final Set<String> reasonCodes = new ConcurrentSkipListSet<>();
        private final Set<String> invalidWorkflowReferences = new ConcurrentSkipListSet<>();

        private void unavailable(Throwable exception, String defaultReasonCode) {
            Throwable current = exception;
            while (current instanceof CompletionException && current.getCause() != null) {
                current = current.getCause();
            }
            if (current instanceof PanoramaCenterClient.TaskSourceException taskException) {
                reasonCodes.add(taskException.reasonCode());
            } else {
                reasonCodes.add(defaultReasonCode);
            }
        }

        private void invalidReference(String reasonCode, String referenceId) {
            reasonCodes.add(reasonCode);
            if (referenceId != null && !referenceId.isBlank()
                    && invalidWorkflowReferences.size() < MAX_REPORTED_INVALID_REFERENCES) {
                invalidWorkflowReferences.add(referenceId);
            }
        }

        private Map<String, Object> snapshot() {
            boolean complete = reasonCodes.isEmpty();
            return object(
                    "complete", complete,
                    "degraded", !complete,
                    "reasonCodes", List.copyOf(reasonCodes),
                    "invalidReferenceCount", invalidWorkflowReferences.size(),
                    "invalidWorkflowReferences", List.copyOf(invalidWorkflowReferences));
        }
    }

    private record PanoramaTasks(
            List<Map<String, Object>> items,
            List<Map<String, Object>> instances,
            Map<String, Object> dataQuality,
            boolean convergencePending) {
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
