package com.robot.bigscreen.panorama;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.DownstreamServiceProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/** 调用下游设备、任务、地图、告警和里程接口，供全景聚合使用。 */
@Component
public class PanoramaCenterClient {

    private static final int TASK_INSTANCE_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(PanoramaCenterClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final int MAX_WORKFLOW_ALARM_CONCURRENCY = 8;
    private static final long WORKFLOW_ALARM_ACQUIRE_TIMEOUT_MS = 100;

    private final RestClient restClient;
    private final RestClient controlRestClient;
    private final RestClient eiopControlRestClient;
    private final RestClient taskRestClient;
    private final RestClient workflowAlarmRestClient;
    private final DownstreamServiceProperties properties;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final Semaphore taskRequestPermits;
    private final Semaphore generalRequestPermits;
    private final Semaphore controlRequestPermits;
    private final Semaphore eiopControlRequestPermits;
    private final Semaphore workflowAlarmRequestPermits;
    private final int taskMaxConcurrency;
    private final int generalMaxConcurrency;
    private final FailureCircuit managementCircuit = new FailureCircuit();
    private final FailureCircuit controlCircuit = new FailureCircuit();
    private final FailureCircuit eiopControlCircuit = new FailureCircuit();
    private final FailureCircuit taskCircuit = new FailureCircuit();
    private final FailureCircuit workflowAlarmCircuit = new FailureCircuit();

    /**
     * 初始化 PanoramaCenterClient，保存所需依赖及初始运行状态。
     *
     * @param builder RestClient 构建器
     * @param properties 服务配置
     * @param authenticatedRequestHeaders 根据已认证的用户与客户端身份生成可信下游请求头。
     * @param generalConnectTimeoutMs 通用查询建立连接的超时，单位毫秒
     * @param generalReadTimeoutMs 通用查询读取响应的超时，单位毫秒
     * @param generalMaxConcurrency 通用查询并发调用上限
     * @param controlConnectTimeoutMs Control 查询建立连接的超时，单位毫秒
     * @param controlReadTimeoutMs Control 查询读取响应的超时，单位毫秒
     * @param controlMaxConcurrency Control 查询并发调用上限
     * @param eiopControlConnectTimeoutMs 管理端控制查询建立连接的超时，单位毫秒
     * @param eiopControlReadTimeoutMs 管理端控制查询读取响应的超时，单位毫秒
     * @param eiopControlMaxConcurrency 管理端控制查询并发调用上限
     * @param workflowAlarmConnectTimeoutMs 工作流告警查询建立连接的超时，单位毫秒
     * @param workflowAlarmReadTimeoutMs 工作流告警查询读取响应的超时，单位毫秒
     * @param workflowAlarmMaxConcurrency 工作流告警查询并发调用上限
     * @param taskConnectTimeoutMs 任务查询建立连接的超时，单位毫秒
     * @param taskReadTimeoutMs 任务查询读取响应的超时，单位毫秒
     * @param taskMaxConcurrency 任务查询并发调用上限
     */
    public PanoramaCenterClient(
            RestClient.Builder builder,
            DownstreamServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            @Value("${panorama.general.connect-timeout-ms:1000}") int generalConnectTimeoutMs,
            @Value("${panorama.general.read-timeout-ms:1500}") int generalReadTimeoutMs,
            @Value("${panorama.general.max-concurrency:16}") int generalMaxConcurrency,
            @Value("${panorama.control.connect-timeout-ms:1000}") int controlConnectTimeoutMs,
            @Value("${panorama.control.read-timeout-ms:1500}") int controlReadTimeoutMs,
            @Value("${panorama.control.max-concurrency:8}") int controlMaxConcurrency,
            @Value("${panorama.eiop-control.connect-timeout-ms:1000}") int eiopControlConnectTimeoutMs,
            @Value("${panorama.eiop-control.read-timeout-ms:1500}") int eiopControlReadTimeoutMs,
            @Value("${panorama.eiop-control.max-concurrency:4}") int eiopControlMaxConcurrency,
            @Value("${panorama.workflow-alarm.connect-timeout-ms:1000}") int workflowAlarmConnectTimeoutMs,
            @Value("${panorama.workflow-alarm.read-timeout-ms:5000}") int workflowAlarmReadTimeoutMs,
            @Value("${panorama.workflow-alarm.max-concurrency:4}") int workflowAlarmMaxConcurrency,
            @Value("${panorama.task.connect-timeout-ms:1000}") int taskConnectTimeoutMs,
            @Value("${panorama.task.read-timeout-ms:1500}") int taskReadTimeoutMs,
            @Value("${panorama.task.max-concurrency:8}") int taskMaxConcurrency) {
        int connectTimeoutMs = Math.max(100, Math.min(5000, generalConnectTimeoutMs));
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(100, Math.min(10000, generalReadTimeoutMs))));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.controlRestClient = builder.clone()
                .requestFactory(requestFactory(controlConnectTimeoutMs, controlReadTimeoutMs))
                .build();
        this.eiopControlRestClient = builder.clone()
                .requestFactory(requestFactory(eiopControlConnectTimeoutMs, eiopControlReadTimeoutMs))
                .build();
        SimpleClientHttpRequestFactory taskRequestFactory = new SimpleClientHttpRequestFactory();
        taskRequestFactory.setConnectTimeout(Math.max(100, Math.min(5000, taskConnectTimeoutMs)));
        taskRequestFactory.setReadTimeout(Math.max(100, Math.min(10000, taskReadTimeoutMs)));
        this.taskRestClient = builder.clone().requestFactory(taskRequestFactory).build();
        SimpleClientHttpRequestFactory workflowAlarmRequestFactory = new SimpleClientHttpRequestFactory();
        workflowAlarmRequestFactory.setConnectTimeout(
                Math.max(100, Math.min(5000, workflowAlarmConnectTimeoutMs)));
        workflowAlarmRequestFactory.setReadTimeout(
                Math.max(100, Math.min(10000, workflowAlarmReadTimeoutMs)));
        this.workflowAlarmRestClient = builder.clone().requestFactory(workflowAlarmRequestFactory).build();
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.generalMaxConcurrency = Math.max(1, Math.min(32, generalMaxConcurrency));
        this.generalRequestPermits = new Semaphore(this.generalMaxConcurrency, true);
        this.controlRequestPermits = new Semaphore(Math.max(1, Math.min(32, controlMaxConcurrency)), true);
        this.eiopControlRequestPermits = new Semaphore(Math.max(1, Math.min(32, eiopControlMaxConcurrency)), true);
        this.workflowAlarmRequestPermits = new Semaphore(
                Math.max(1, Math.min(MAX_WORKFLOW_ALARM_CONCURRENCY, workflowAlarmMaxConcurrency)), true);
        this.taskMaxConcurrency = Math.max(1, Math.min(32, taskMaxConcurrency));
        this.taskRequestPermits = new Semaphore(this.taskMaxConcurrency, true);
    }

    int taskMaxConcurrency() {
        return taskMaxConcurrency;
    }

    private SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(100, Math.min(5000, connectTimeoutMs)));
        requestFactory.setReadTimeout(Math.max(100, Math.min(10000, readTimeoutMs)));
        return requestFactory;
    }

    /**
     * 查询当前用户可见的管理端设备档案，供全景及统计聚合。
     *
     * @return 授权范围内的设备列表
     */
    public List<Map<String, Object>> devices() {
        int pageSize = 100;
        return emptyListWhenForbidden(
                () -> requiredPagedRecords(pageNum -> uri(
                                properties.getManageBaseUrl(), "/api/v1/management/devices")
                        .queryParam("pageNum", pageNum)
                        .queryParam("pageSize", pageSize)
                        .build(true)
                        .toUri(), pageSize),
                "设备");
    }

    /**
     * 查询管理端设备类型字典。
     *
     * @return 可用设备类型选项
     */
    public List<Map<String, Object>> deviceTypeOptions() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/selection-options/dictionaries/device_type")
                .build(true)
                .toUri();
        return records(uri);
    }

    /**
     * 查询当前用户可见的单个设备档案。
     *
     * @param id 当前业务记录的唯一标识
     * @return 设备详情字段
     */
    public Optional<Map<String, Object>> device(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/devices/" + id)
                .build(true)
                .toUri();
        return dataMap(uri);
    }

    /**
     * 按装备序列号批量读取管理端运行态。
     *
     * @param serialNumbers 装备序列号集合
     * @return 匹配设备的运行状态列表
     */
    public List<Map<String, Object>> realtimeStatuses(List<String> serialNumbers) {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder builder = uri(
                properties.getEiopControlBaseUrl(), "/api/v1/control/device-realtime-statuses");
        for (String serialNumber : serialNumbers) {
            if (serialNumber != null && !serialNumber.isBlank()) {
                builder.queryParam("serialNumbers", serialNumber);
            }
        }
        return records(
                builder.build(true).toUri(),
                eiopControlRestClient,
                eiopControlCircuit,
                eiopControlRequestPermits,
                "EIOP Control");
    }

    /**
     * 读取 Control 内存注册表，用边缘事实补齐设备运行态。
     *
     * @return Control 当前注册的机器人状态
     */
    public List<Map<String, Object>> registeredRobots() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/robots/registry")
                .build(true)
                .toUri();
        return records(uri, controlRestClient, controlCircuit, controlRequestPermits, "Control");
    }

    /**
     * 读取 控制服务 设备注册表快照（在线/离线状态由 MQTT 状态上报维护）。
     *  registry 响应为 {@code {records:[...], total}}，需直接取顶层 records。
     *
     * @return Control 注册表中的机器人运行态记录
     */
    public List<Map<String, Object>> deviceRegistry() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/robots/registry")
                .build(true)
                .toUri();
        return responseMap(uri, controlRestClient, controlCircuit, controlRequestPermits, "Control")
                .map(response -> {
                    Object records = response.get("records");
                    if (records instanceof List<?> list) {
                        return maps(list);
                    }
                    return List.<Map<String, Object>>of();
                })
                .orElse(List.of());
    }

    /**
     * 查询 Control 的时间窗口里程，保留无数据与真实零值差异。
     *
     * @param startTime 上海时区区间起点，包含该时刻
     * @param endTime 上海时区区间终点，包含该时刻
     * @param robotIds 机器人标识集合
     * @return 里程汇总及采样质量字段
     */
    public Map<String, Object> mileageSummary(
            String startTime,
            String endTime,
            List<String> robotIds) {
        UriComponentsBuilder builder = uri(properties.getControlBaseUrl(), "/api/control/statistics/mileage")
                .queryParam("startTime", startTime.replace(' ', 'T'))
                .queryParam("endTime", endTime.replace(' ', 'T'));
        if (robotIds != null) {
            robotIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(value -> builder.queryParam("robotIds", value));
        }
        return dataMap(
                        builder.build(true).toUri(),
                        controlRestClient,
                        controlCircuit,
                        controlRequestPermits,
                        "Control")
                .orElse(Map.of());
    }

    /**
     * 查询当前用户可见的工作流计划，失败由任务降级机制解释。
     *
     * @return 工作流计划列表
     */
    public List<Map<String, Object>> taskWorkflowPlans() {
        int pageSize = 100;
        return taskPagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-plans")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("enabled", true)
                .build(true)
                .toUri(), pageSize, "TASK_PLANS_UNAVAILABLE");
    }

    /**
     * 按标识读取任务计划详情。临时计划不进入计划分页，运行态补齐时只能通过详情读取其类型、
     *  目标与角色绑定；调用方必须继续按当前登录身份执行权限校验。
     *
     * @param taskId 任务计划 ID
     * @return 当前用户可见的计划详情；不存在时为空
     */
    public Optional<Map<String, Object>> taskWorkflowPlan(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-plans/" + taskId)
                .build(true)
                .toUri();
        return taskDataMap(uri, "TASK_PLAN_UNAVAILABLE", true);
    }

    /**
     * 查询一个任务工作流计划关联的可用固定摄像头。该接口由 Management 按工作流依赖的路径汇总，
     *  不能用某一个 workflowDefinition 的 pathId 替代，否则会遗漏依赖工作流中的摄像头。
     *
     * @param taskId 任务计划 ID
     * @return 任务计划关联的固定摄像头列表
     */
    public List<Map<String, Object>> taskWorkflowPlanFixedCameras(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(),
                "/api/v1/management/task-workflow-plans/" + taskId + "/fixed-cameras")
                .build(true)
                .toUri();
        return records(taskResponseMap(uri, "TASK_FIXED_CAMERAS_UNAVAILABLE", true).orElse(Map.of()));
    }

    /**
     * 查询任务计划冻结版本解析出的路线点。路线归属由 Management 根据计划版本及其依赖统一解析，
     *  BFF 不再读取工作流定义上的历史 pathId 字段拼装路线。
     *
     * @param taskId 任务计划 ID
     * @return 任务计划对应的路线点列表
     */
    public List<Map<String, Object>> taskWorkflowPlanRoutePoints(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(),
                "/api/v1/management/task-workflow-plans/" + taskId + "/route-points")
                .build(true)
                .toUri();
        return records(taskResponseMap(uri, "TASK_ROUTE_POINTS_UNAVAILABLE", false).orElse(Map.of()));
    }

    /**
     * 查询任务运行实例，供当前任务与统计汇总使用。
     *
     * @return 工作流运行实例列表
     */
    public List<Map<String, Object>> taskWorkflowInstances() {
        return taskWorkflowInstances("ALL");
    }

    /**
     * 查询仍在运行的工作流实例，供临时任务及运行态收敛使用。
     *
     * @return 活动工作流实例列表
     */
    public List<Map<String, Object>> activeTaskWorkflowInstances() {
        return taskWorkflowInstances("ACTIVE");
    }

    private List<Map<String, Object>> taskWorkflowInstances(String scope) {
        int pageSize = TASK_INSTANCE_PAGE_SIZE;
        return taskPagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("scope", scope)
                .build(true)
                .toUri(), pageSize, "TASK_INSTANCES_UNAVAILABLE");
    }

    /**
     * 查询统计范围所需的工作流实例，沿用独立任务请求预算。
     *
     * @return 用于统计聚合的实例列表
     */
    public List<Map<String, Object>> taskWorkflowInstancesForStatistics() {
        return taskWorkflowInstances();
    }

    /**
     * 查询指定工作流运行实例。
     *
     * @param workflowInstanceId 工作流运行实例 ID
     * @return 实例详情
     */
    public Optional<Map<String, Object>> taskWorkflowInstance(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId)
                .build(true)
                .toUri();
        return taskDataMap(uri, "WORKFLOW_INSTANCE_UNAVAILABLE", true);
    }

    /**
     * 查询工作流实例回放数据，供展开任务详情使用。
     *
     * @param workflowInstanceId 工作流运行实例 ID
     * @return 回放数据
     */
    public Optional<Map<String, Object>> taskWorkflowReplay(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId + "/replay")
                .build(true)
                .toUri();
        return taskDataMap(uri, "TASK_REPLAY_UNAVAILABLE", true);
    }

    /**
     * 查询工作流关联的设备任务实例。
     *
     * @param workflowInstanceId 工作流运行实例 ID
     * @return 设备任务实例列表
     */
    public List<Map<String, Object>> deviceTaskInstances(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/device-task-instances")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 100)
                .queryParam("workflowInstanceId", workflowInstanceId)
                .build(true)
                .toUri();
        return taskRecords(uri, "DEVICE_TASKS_UNAVAILABLE");
    }

    /**
     * 读取任务查询并发闸门的当前占用状态。
     *
     * @return 任务请求池诊断快照
     */
    public Map<String, Object> taskRequestPoolSnapshot() {
        return Map.of(
                "maxConcurrency", taskMaxConcurrency,
                "activeRequests", taskMaxConcurrency - taskRequestPermits.availablePermits(),
                "availablePermits", taskRequestPermits.availablePermits());
    }

    /**
     * 读取通用查询并发闸门的当前占用状态。
     *
     * @return 通用请求池诊断快照
     */
    public Map<String, Object> generalRequestPoolSnapshot() {
        return Map.of(
                "maxConcurrency", generalMaxConcurrency,
                "activeRequests", generalMaxConcurrency - generalRequestPermits.availablePermits(),
                "availablePermits", generalRequestPermits.availablePermits());
    }

    /**
     * 查询当前用户可见的启用地图，不加载每张地图的点位。
     *
     * @return 启用地图摘要列表
     */
    public List<Map<String, Object>> enabledMaps() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/maps")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .queryParam("enabled", true)
                .build(true)
                .toUri();
        return records(requiredResponseMap(uri));
    }

    /**
     * 按指定地图读取点位资源。
     *
     * @param mapId 所属地图 ID
     * @return 地图点位列表
     */
    public List<Map<String, Object>> mapPoints(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/maps/" + mapId + "/points")
                .build(true)
                .toUri();
        return records(uri);
    }

    /**
     * 读取当前用户可见的固定摄像头；传入地图时按地图关系筛选。
     *
     * @param mapId 所属地图 ID
     * @return 固定摄像头档案列表
     */
    public List<Map<String, Object>> fixedCameras(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return List.of();
        }
        int pageSize = 100;
        return emptyListWhenForbidden(
                () -> pagedRecords(pageNum -> uri(
                                properties.getManageBaseUrl(), "/api/v1/management/fixed-cameras")
                        .queryParam("pageNum", pageNum)
                        .queryParam("pageSize", pageSize)
                        .queryParam("mapId", mapId)
                        .build(true)
                        .toUri(), pageSize),
                "固定摄像头");
    }

    /**
     * 读取当前用户可见的固定摄像头；传入地图时按地图关系筛选。
     *
     * @return 固定摄像头档案列表
     */
    public List<Map<String, Object>> fixedCameras() {
        int pageSize = 100;
        return emptyListWhenForbidden(
                () -> pagedRecords(pageNum -> uri(
                                properties.getManageBaseUrl(), "/api/v1/management/fixed-cameras")
                        .queryParam("pageNum", pageNum)
                        .queryParam("pageSize", pageSize)
                        .build(true)
                        .toUri(), pageSize),
                "固定摄像头");
    }

    private List<Map<String, Object>> emptyListWhenForbidden(
            Supplier<List<Map<String, Object>>> query,
            String resourceName) {
        try {
            return query.get();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
            log.info("当前用户无{}查看权限，按空集合返回", resourceName);
            return List.of();
        }
    }

    /**
     * 查询 Control 汇总的当前用户固定摄像头健康状态。
     *
     * @return Control 返回的授权摄像头健康快照
     */
    public Map<String, Object> fixedCameraHealth() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/fixed-cameras/health")
                .build(true)
                .toUri();
        return responseMap(uri, controlRestClient, controlCircuit, controlRequestPermits, "Control")
                .orElse(Map.of("records", List.of()));
    }

    /**
     * 按风险及时间筛选分页查询告警。
     *
     * @param status 当前业务状态，取值遵循所属模型的状态协议
     * @param severity 告警风险等级
     * @param occurredFrom 告警发生时间下界
     * @param occurredTo 告警发生时间上界
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页请求的记录数量
     * @return 告警记录、总数和分页信息
     */
    public AlarmPage alarmPage(
            String status,
            String severity,
            String occurredFrom,
            String occurredTo,
            int pageNum,
            int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        UriComponentsBuilder builder = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms")
                .queryParam("pageNum", safePageNum)
                .queryParam("pageSize", safePageSize);
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }
        if (severity != null && !severity.isBlank()) {
            builder.queryParam("severity", severity);
        }
        if (occurredFrom != null && !occurredFrom.isBlank()) {
            builder.queryParam("occurredFrom", occurredFrom.replace(' ', 'T'));
        }
        if (occurredTo != null && !occurredTo.isBlank()) {
            builder.queryParam("occurredTo", occurredTo.replace(' ', 'T'));
        }
        Map<String, Object> response = responseMap(builder.build(true).toUri()).orElse(Map.of());
        List<Map<String, Object>> records = records(response);
        return new AlarmPage(records, reportedTotal(response, records.size()), safePageNum, safePageSize);
    }

    /**
     * 使用独立超时、并发闸门及熔断查询当前用户可处置的工作流告警。
     *
     * @return 权威查询返回的可处置告警列表；失败不伪装为空集合
     */
    public List<Map<String, Object>> actionableWorkflowAlarms() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/actionable-workflow")
                .build(true)
                .toUri();
        boolean acquired = false;
        try {
            acquired = workflowAlarmRequestPermits.tryAcquire(
                    WORKFLOW_ALARM_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "工作流告警查询并发已达上限");
            }
            return records(responseMap(uri, workflowAlarmRestClient, workflowAlarmCircuit, null).orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE, "工作流告警查询失败")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "工作流告警查询排队被中断", exception);
        } finally {
            if (acquired) {
                workflowAlarmRequestPermits.release();
            }
        }
    }

    /**
     * 查询指定时间区间的告警，用于报告统计。
     *
     * @param occurredFrom 告警发生时间下界
     * @param occurredTo 告警发生时间上界
     * @return 统计用告警列表
     */
    public List<Map<String, Object>> alarmsForStatistics(String occurredFrom, String occurredTo) {
        int pageSize = 100;
        return pagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/alarms")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("occurredFrom", occurredFrom.replace(' ', 'T'))
                .queryParam("occurredTo", occurredTo.replace(' ', 'T'))
                .build(true)
                .toUri(), pageSize);
    }

    /**
     * 将普通告警处置结果提交到 Management。
     *
     * @param alarmId 待查询或处置的告警 ID
     * @param handleAction 告警处置动作标识
     * @param handleResult 人工填写的告警处置说明
     * @return 下游返回的处置结果
     */
    public boolean handleAlarm(String alarmId, String handleAction, String handleResult) {
        if (alarmId == null || alarmId.isBlank() || handleAction == null || handleAction.isBlank()) {
            return false;
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/" + alarmId + "/handled")
                .build(true)
                .toUri();
        return updateAlarm(uri, false, handleAction, handleResult);
    }

    /**
     * 提交告警处置并请求继续对应工作流。
     *
     * @param alarmId 待查询或处置的告警 ID
     * @param handleAction 告警处置动作标识
     * @param handleResult 人工填写的告警处置说明
     * @return 下游返回的处置及继续执行结果
     */
    public boolean handleWorkflowAlarm(String alarmId, String handleAction, String handleResult) {
        if (alarmId == null || alarmId.isBlank() || handleAction == null || handleAction.isBlank()) {
            return false;
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/" + alarmId + "/handle-and-continue")
                .build(true)
                .toUri();
        return updateAlarm(uri, true, handleAction, handleResult);
    }

    private boolean updateAlarm(URI uri, boolean workflow, String handleAction, String handleResult) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handleAction", handleAction);
        body.put("handleResult", handleResult);
        long startNanos = System.nanoTime();
        log.info("管理端告警处置请求开始 协议=http 方向=出站 阶段=请求 结果=已发起 请求方法={} 请求地址={} 业务类型=告警",
                workflow ? "POST" : "PATCH", uri);
        try {
            if (workflow) {
                restClient.post()
                        .uri(uri)
                        .headers(authenticatedRequestHeaders::apply)
                        .body(body)
                        .retrieve()
                        .body(MAP_TYPE);
            } else {
                restClient.patch()
                        .uri(uri)
                        .headers(authenticatedRequestHeaders::apply)
                        .body(body)
                        .retrieve()
                        .body(MAP_TYPE);
            }
            log.info("管理端告警处置调用成功 协议=http 方向=出站 阶段=响应 结果=成功 请求方法={} 请求地址={} 状态码=200 业务类型=告警 耗时毫秒={}",
                    workflow ? "POST" : "PATCH", uri, elapsedMillis(startNanos));
            return true;
        } catch (RuntimeException exception) {
            log.warn("管理端告警处置调用失败 协议=http 方向=出站 阶段=响应 结果=失败 请求方法={} 请求地址={} 业务类型=告警 耗时毫秒={}",
                    workflow ? "POST" : "PATCH", uri, elapsedMillis(startNanos), exception);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(URI uri) {
        return responseMap(uri, restClient, managementCircuit, generalRequestPermits, "Management")
                .map(this::records)
                .orElse(List.of());
    }

    private List<Map<String, Object>> records(
            URI uri,
            RestClient client,
            FailureCircuit circuit,
            Semaphore permits,
            String downstream) {
        return responseMap(uri, client, circuit, permits, downstream).map(this::records).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> response) {
        Object topLevelRecords = response.get("records");
        if (topLevelRecords instanceof List<?> list) {
            return maps(list);
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object nestedRecords = dataMap.get("records");
            if (nestedRecords instanceof List<?> list) {
                return maps(list);
            }
            if (dataMap.isEmpty()) {
                return List.of();
            }
            return List.of((Map<String, Object>) dataMap);
        }
        if (data instanceof List<?> list) {
            return maps(list);
        }
        if (!response.containsKey("code") && !response.containsKey("data")) {
            return List.of(response);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> dataMap(URI uri) {
        return dataMap(uri, restClient, managementCircuit, generalRequestPermits, "Management");
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> dataMap(
            URI uri,
            RestClient client,
            FailureCircuit circuit,
            Semaphore permits,
            String downstream) {
        return responseMap(uri, client, circuit, permits, downstream)
                .flatMap(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> map) {
                        return Optional.of((Map<String, Object>) map);
                    }
                    if (!response.containsKey("code") && !response.containsKey("data")) {
                        return Optional.of(response);
                    }
                    return Optional.empty();
                });
    }

    private Optional<Map<String, Object>> responseMap(URI uri) {
        return responseMap(uri, restClient, managementCircuit, generalRequestPermits, "Management");
    }

    private Optional<Map<String, Object>> responseMap(
            URI uri,
            RestClient client,
            FailureCircuit circuit,
            Semaphore permits) {
        return responseMap(uri, client, circuit, permits, "Management");
    }

    /**
     * 在通用并发预算内调用下游并解析统一响应；释放许可后再向调用方返回或传播失败。
     */
    private Optional<Map<String, Object>> responseMap(
            URI uri,
            RestClient client,
            FailureCircuit circuit,
            Semaphore permits,
            String downstream) {
        PanoramaService.requireRequestTimeRemaining();
        if (!circuit.allowRequest()) {
            log.warn("{} 查询熔断中，本次不再请求下游，请求地址={}", downstream, uri);
            return Optional.empty();
        }
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = permits == null || permits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                circuit.recordFailure();
                log.warn("{} 查询并发已达上限，请求地址={}", downstream, uri);
                return Optional.empty();
            }
            log.info("下游 HTTP 查询开始 协议=http 方向=出站 阶段=请求 结果=已发起 下游服务={} 请求方法=GET 请求地址={}", downstream, uri);
            Map<String, Object> response = client.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                circuit.recordFailure();
                log.warn("下游 HTTP 响应无效 协议=http 方向=出站 阶段=响应 结果=无效响应 下游服务={} 请求方法=GET 请求地址={} 状态码=200 耗时毫秒={}",
                        downstream, uri, elapsedMillis(startNanos));
                return Optional.empty();
            }
            circuit.recordSuccess();
            log.info("下游 HTTP 查询成功 协议=http 方向=出站 阶段=响应 结果=成功 下游服务={} 请求方法=GET 请求地址={} 状态码=200 返回条目数={} 耗时毫秒={}",
                    downstream, uri, records(response).size(), elapsedMillis(startNanos));
            return Optional.of(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                circuit.recordSuccess();
                throw new ResponseStatusException(exception.getStatusCode(), "查询 " + downstream + " 权限失败", exception);
            }
            if (exception.getStatusCode().is5xxServerError()) {
                circuit.recordFailure();
            } else {
                circuit.recordSuccess();
            }
            log.warn("请求全景地图下游接口失败，下游={} 请求地址={} 状态码={} 耗时毫秒={}",
                    downstream, uri, exception.getStatusCode().value(), elapsedMillis(startNanos));
            return Optional.empty();
        } catch (ResourceAccessException exception) {
            circuit.recordFailure();
            log.warn("请求全景地图下游接口超时，下游={} 请求地址={} 耗时毫秒={}", downstream, uri, elapsedMillis(startNanos));
            return Optional.empty();
        } catch (RuntimeException exception) {
            circuit.recordFailure();
            log.warn("请求全景地图下游接口失败，下游={} 请求地址={} 耗时毫秒={}", downstream, uri, elapsedMillis(startNanos), exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (acquired && permits != null) {
                permits.release();
            }
        }
    }

    private List<Map<String, Object>> taskRecords(URI uri, String unavailableReasonCode) {
        return records(taskResponseMap(uri, unavailableReasonCode, false).orElse(Map.of()));
    }

    private List<Map<String, Object>> taskPagedRecords(
            IntFunction<URI> uriFactory,
            int pageSize,
            String unavailableReasonCode) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> previousPage = null;
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            PanoramaService.requireRequestTimeRemaining();
            URI uri = uriFactory.apply(pageNum);
            Map<String, Object> response = taskResponseMap(uri, unavailableReasonCode, false).orElse(Map.of());
            List<Map<String, Object>> page = records(response);
            if (page.equals(previousPage)) {
                throw new TaskSourceException("TASK_PAGINATION_NO_PROGRESS", "Management 任务分页无进展");
            }
            result.addAll(page);
            if (page.size() < pageSize || reachedReportedTotal(response, result.size())) {
                return List.copyOf(result);
            }
            previousPage = page;
        }
        throw new TaskSourceException("TASK_PAGINATION_LIMIT", "Management 任务分页超过安全上限");
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> taskDataMap(
            URI uri,
            String unavailableReasonCode,
            boolean notFoundAllowed) {
        return taskResponseMap(uri, unavailableReasonCode, notFoundAllowed)
                .flatMap(response -> {
                    Object data = response.get("data");
                    if (data instanceof Map<?, ?> map) {
                        return Optional.of((Map<String, Object>) map);
                    }
                    if (!response.containsKey("code") && !response.containsKey("data")) {
                        return Optional.of(response);
                    }
                    return Optional.empty();
                });
    }

    /**
     * 使用任务专属并发与超时预算查询下游，把拒绝、超时及业务失败转换为可追踪降级原因。
     */
    private Optional<Map<String, Object>> taskResponseMap(
            URI uri,
            String unavailableReasonCode,
            boolean notFoundAllowed) {
        PanoramaService.requireRequestTimeRemaining();
        if (!taskCircuit.allowRequest()) {
            throw new TaskSourceException("TASK_CIRCUIT_OPEN", "Management 任务查询暂时熔断");
        }
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = taskRequestPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                taskCircuit.recordFailure();
                throw new TaskSourceException("TASK_QUERY_CONCURRENCY_LIMIT", "任务查询并发已达上限");
            }
            log.info("管理端任务查询开始 协议=http 方向=出站 阶段=请求 结果=已发起 请求方法=GET 请求地址={}", uri);
            Map<String, Object> response = taskRestClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                taskCircuit.recordFailure();
                throw new TaskSourceException("TASK_INVALID_RESPONSE", "Management 任务查询返回空响应");
            }
            taskCircuit.recordSuccess();
            log.info("管理端任务查询成功 协议=http 方向=出站 阶段=响应 结果=成功 请求方法=GET 请求地址={} 状态码=200 返回条目数={} 耗时毫秒={}",
                    uri, records(response).size(), elapsedMillis(startNanos));
            return Optional.of(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaskSourceException("TASK_QUERY_INTERRUPTED", "任务查询等待被中断", exception);
        } catch (RestClientResponseException exception) {
            if (notFoundAllowed && exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                taskCircuit.recordSuccess();
                log.info("管理端任务引用已不存在，请求地址={}", uri);
                return Optional.empty();
            }
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                taskCircuit.recordSuccess();
                throw new ResponseStatusException(exception.getStatusCode(), "查询 Management 任务权限失败", exception);
            }
            if (exception.getStatusCode().is5xxServerError()) {
                taskCircuit.recordFailure();
            } else {
                taskCircuit.recordSuccess();
            }
            log.warn("管理端任务接口响应异常，请求地址={} 状态码={} 耗时毫秒={}",
                    uri, exception.getStatusCode().value(), elapsedMillis(startNanos));
            throw new TaskSourceException(unavailableReasonCode, "Management 任务接口响应异常", exception);
        } catch (ResourceAccessException exception) {
            taskCircuit.recordFailure();
            log.warn("管理端任务接口连接或读取超时，请求地址={} 耗时毫秒={}",
                    uri, elapsedMillis(startNanos));
            throw new TaskSourceException("TASK_QUERY_TIMEOUT", "Management 任务接口连接或读取超时", exception);
        } finally {
            if (acquired) {
                taskRequestPermits.release();
            }
        }
    }

    private List<Map<String, Object>> pagedRecords(IntFunction<URI> uriFactory, int pageSize) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> previousPage = null;
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            PanoramaService.requireRequestTimeRemaining();
            URI uri = uriFactory.apply(pageNum);
            Map<String, Object> response = responseMap(uri).orElse(Map.of());
            List<Map<String, Object>> page = records(response);
            if (page.equals(previousPage)) {
                log.warn("全景地图中心端分页无进展，停止继续查询，请求地址={} 页码={}", uri, pageNum);
                return result;
            }
            result.addAll(page);
            if (page.size() < pageSize || reachedReportedTotal(response, result.size())) {
                return result;
            }
            previousPage = page;
        }
        log.warn("全景地图中心端分页达到安全上限，返回已获取数据");
        return result;
    }

    private List<Map<String, Object>> requiredPagedRecords(IntFunction<URI> uriFactory, int pageSize) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> previousPage = null;
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            PanoramaService.requireRequestTimeRemaining();
            URI uri = uriFactory.apply(pageNum);
            Map<String, Object> response = requiredResponseMap(uri);
            List<Map<String, Object>> page = records(response);
            if (page.equals(previousPage)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "管理端设备分页无进展");
            }
            result.addAll(page);
            if (page.size() < pageSize || reachedReportedTotal(response, result.size())) {
                return result;
            }
            previousPage = page;
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "管理端设备分页超过安全上限");
    }

    @SuppressWarnings("unchecked")
    private boolean reachedReportedTotal(Map<String, Object> response, int receivedCount) {
        Object total = response.get("total");
        if (total == null && response.get("data") instanceof Map<?, ?> data) {
            total = data.get("total");
        }
        if (total instanceof Number number) {
            return receivedCount >= number.longValue();
        }
        if (total instanceof String text) {
            try {
                return receivedCount >= Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private long reportedTotal(Map<String, Object> response, int fallback) {
        Object total = response.get("total");
        if (total == null && response.get("data") instanceof Map<?, ?> data) {
            total = data.get("total");
        }
        if (total instanceof Number number) {
            return number.longValue();
        }
        if (total instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // 使用当前页数量作为兼容回退。
            }
        }
        return fallback;
    }

    /**
     * 对不能用空数据替代的权威查询执行独立预算与熔断；失败必须传播，防止覆盖有效快照。
     */
    private Map<String, Object> requiredResponseMap(URI uri) {
        PanoramaService.requireRequestTimeRemaining();
        if (!managementCircuit.allowRequest()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Management 通用查询暂时熔断");
        }
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = generalRequestPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                managementCircuit.recordFailure();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理端通用查询并发已达上限");
            }
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                managementCircuit.recordFailure();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理端资源响应为空");
            }
            managementCircuit.recordSuccess();
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                managementCircuit.recordFailure();
            } else {
                managementCircuit.recordSuccess();
            }
            HttpStatus status = exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    ? HttpStatus.UNAUTHORIZED
                    : exception.getStatusCode() == HttpStatus.FORBIDDEN
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ResponseStatusException(status, "查询管理端授权资源失败", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "查询管理端授权资源被中断", exception);
        } catch (RuntimeException exception) {
            managementCircuit.recordFailure();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "查询管理端授权资源失败",
                    exception);
        } finally {
            if (acquired) {
                generalRequestPermits.release();
            }
        }
    }

    private void logSlowRequest(URI uri, long startNanos) {
        long elapsedMs = elapsedMillis(startNanos);
        if (elapsedMs >= 1000) {
            log.warn("全景地图中心端接口响应较慢，请求地址={} 耗时毫秒={}", uri, elapsedMs);
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private UriComponentsBuilder uri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8088" : baseUrl;
        String separator = normalizedBaseUrl.endsWith("/") || path.startsWith("/") ? "" : "/";
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl + separator + path);
    }

    /**
     * 下游告警分页结果，保留总数及原分页参数。
     *
     * @param records 当前查询返回的记录集合
     * @param total 总数
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页请求的记录数量
     */
    public record AlarmPage(List<Map<String, Object>> records, long total, int pageNum, int pageSize) {
    }

    /** 标识任务数据源不可用或返回异常，供聚合层报告数据质量。 */
    public static final class TaskSourceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String reasonCode;

        /**
         * 初始化 TaskSourceException，保存所需依赖及初始运行状态。
         *
         * @param reasonCode 当前状态或失败原因码
         * @param message 消息内容
         */
        public TaskSourceException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        /**
         * 初始化 TaskSourceException，保存所需依赖及初始运行状态。
         *
         * @param reasonCode 当前状态或失败原因码
         * @param message 消息内容
         * @param cause 触发当前异常的原始原因
         */
        public TaskSourceException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        /**
         * 取得当前下游查询失败的稳定原因编码。
         *
         * @return 供降级状态和日志使用的原因编码
         */
        public String reasonCode() {
            return reasonCode;
        }
    }

    /** 连续三次可恢复失败后短暂打开；到期只放行一个恢复探测。 */
    private static final class FailureCircuit {

        private static final int FAILURE_THRESHOLD = 3;
        private static final long OPEN_MILLIS = 5000;
        /**
         * 连续可恢复失败次数；成功后清零。
         */
        private int failures;
        /**
         * 熔断开始的系统时间戳，单位毫秒；零表示未打开。
         */
        private long openedAt;
        /**
         * 熔断冷却结束后是否已有一个探测请求在执行。
         */
        private boolean probeInProgress;

        private synchronized boolean allowRequest() {
            if (failures < FAILURE_THRESHOLD) {
                return true;
            }
            if (System.currentTimeMillis() - openedAt < OPEN_MILLIS || probeInProgress) {
                return false;
            }
            probeInProgress = true;
            return true;
        }

        private synchronized void recordSuccess() {
            failures = 0;
            openedAt = 0;
            probeInProgress = false;
        }

        private synchronized void recordFailure() {
            failures++;
            probeInProgress = false;
            if (failures >= FAILURE_THRESHOLD) {
                openedAt = System.currentTimeMillis();
            }
        }
    }
}
