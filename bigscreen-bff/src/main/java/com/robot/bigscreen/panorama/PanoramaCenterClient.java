package com.robot.bigscreen.panorama;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PanoramaCenterClient {

    private static final Logger log = LoggerFactory.getLogger(PanoramaCenterClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final RestClient taskRestClient;
    private final CenterServiceProperties properties;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final Semaphore taskRequestPermits;
    private final Semaphore generalRequestPermits;
    private final int taskMaxConcurrency;
    private final int generalMaxConcurrency;

    public PanoramaCenterClient(
            RestClient.Builder builder,
            CenterServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            @Value("${panorama.general.connect-timeout-ms:1000}") int generalConnectTimeoutMs,
            @Value("${panorama.general.read-timeout-ms:1500}") int generalReadTimeoutMs,
            @Value("${panorama.general.max-concurrency:16}") int generalMaxConcurrency,
            @Value("${panorama.task.connect-timeout-ms:1000}") int taskConnectTimeoutMs,
            @Value("${panorama.task.read-timeout-ms:1500}") int taskReadTimeoutMs,
            @Value("${panorama.task.max-concurrency:8}") int taskMaxConcurrency) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(100, Math.min(5000, generalConnectTimeoutMs)));
        requestFactory.setReadTimeout(Math.max(100, Math.min(10000, generalReadTimeoutMs)));
        this.restClient = builder.requestFactory(requestFactory).build();
        SimpleClientHttpRequestFactory taskRequestFactory = new SimpleClientHttpRequestFactory();
        taskRequestFactory.setConnectTimeout(Math.max(100, Math.min(5000, taskConnectTimeoutMs)));
        taskRequestFactory.setReadTimeout(Math.max(100, Math.min(10000, taskReadTimeoutMs)));
        this.taskRestClient = builder.clone().requestFactory(taskRequestFactory).build();
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.generalMaxConcurrency = Math.max(1, Math.min(32, generalMaxConcurrency));
        this.generalRequestPermits = new Semaphore(this.generalMaxConcurrency, true);
        this.taskMaxConcurrency = Math.max(1, Math.min(32, taskMaxConcurrency));
        this.taskRequestPermits = new Semaphore(this.taskMaxConcurrency, true);
    }

    public List<Map<String, Object>> devices() {
        int pageSize = 100;
        return requiredPagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/devices")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .build(true)
                .toUri(), pageSize);
    }

    public List<Map<String, Object>> deviceTypeOptions() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/selection-options/dictionaries/device_type")
                .build(true)
                .toUri();
        return records(uri);
    }

    public Optional<Map<String, Object>> device(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/devices/" + id)
                .build(true)
                .toUri();
        return dataMap(uri);
    }

    public List<Map<String, Object>> realtimeStatuses(List<String> serialNumbers) {
        if (serialNumbers == null || serialNumbers.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder builder = uri(properties.getV1ControlBaseUrl(), "/api/v1/control/device-realtime-statuses");
        for (String serialNumber : serialNumbers) {
            if (serialNumber != null && !serialNumber.isBlank()) {
                builder.queryParam("serialNumbers", serialNumber);
            }
        }
        return records(builder.build(true).toUri());
    }

    public List<Map<String, Object>> registeredRobots() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/robots/registry")
                .build(true)
                .toUri();
        return records(uri);
    }

    /**
     * 读取 Control Service 设备注册表快照（在线/离线状态由 MQTT 状态上报维护）。
     * registry 响应为 {@code {records:[...], total}}，需直接取顶层 records。
     */
    public List<Map<String, Object>> deviceRegistry() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/robots/registry")
                .build(true)
                .toUri();
        return responseMap(uri)
                .map(response -> {
                    Object records = response.get("records");
                    if (records instanceof List<?> list) {
                        return maps(list);
                    }
                    return List.<Map<String, Object>>of();
                })
                .orElse(List.of());
    }

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
        return dataMap(builder.build(true).toUri()).orElse(Map.of());
    }

    public List<Map<String, Object>> taskWorkflowPlans() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-plans")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 20)
                .queryParam("enabled", true)
                .build(true)
                .toUri();
        return taskRecords(uri, "TASK_PLANS_UNAVAILABLE");
    }

    /**
     * 查询一个任务工作流计划关联的可用固定摄像头。该接口由 Management 按工作流依赖的路径汇总，
     * 不能用某一个 workflowDefinition 的 pathId 替代，否则会遗漏依赖工作流中的摄像头。
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

    public Optional<Map<String, Object>> taskWorkflowDefinition(String workflowDefinitionId) {
        if (workflowDefinitionId == null || workflowDefinitionId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-definitions/" + workflowDefinitionId)
                .build(true)
                .toUri();
        return taskDataMap(uri, "WORKFLOW_DEFINITION_UNAVAILABLE", true);
    }

    public List<Map<String, Object>> taskWorkflowInstances() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 100)
                .queryParam("scope", "ALL")
                .build(true)
                .toUri();
        return taskRecords(uri, "TASK_INSTANCES_UNAVAILABLE");
    }

    public List<Map<String, Object>> taskWorkflowInstancesForStatistics() {
        int pageSize = 100;
        return taskPagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("scope", "ALL")
                .build(true)
                .toUri(), pageSize, "TASK_INSTANCES_UNAVAILABLE");
    }

    public Optional<Map<String, Object>> taskWorkflowInstance(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId)
                .build(true)
                .toUri();
        return taskDataMap(uri, "WORKFLOW_INSTANCE_UNAVAILABLE", true);
    }

    public Optional<Map<String, Object>> taskWorkflowReplay(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId + "/replay")
                .build(true)
                .toUri();
        return taskDataMap(uri, "TASK_REPLAY_UNAVAILABLE", true);
    }

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

    public Map<String, Object> taskRequestPoolSnapshot() {
        return Map.of(
                "maxConcurrency", taskMaxConcurrency,
                "activeRequests", taskMaxConcurrency - taskRequestPermits.availablePermits(),
                "availablePermits", taskRequestPermits.availablePermits());
    }

    public Map<String, Object> generalRequestPoolSnapshot() {
        return Map.of(
                "maxConcurrency", generalMaxConcurrency,
                "activeRequests", generalMaxConcurrency - generalRequestPermits.availablePermits(),
                "availablePermits", generalRequestPermits.availablePermits());
    }

    public List<Map<String, Object>> enabledMaps() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/maps")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .queryParam("enabled", true)
                .build(true)
                .toUri();
        return records(requiredResponseMap(uri));
    }

    public List<Map<String, Object>> mapPoints(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/maps/" + mapId + "/points")
                .build(true)
                .toUri();
        return records(uri);
    }

    public List<Map<String, Object>> fixedCameras(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return List.of();
        }
        int pageSize = 100;
        return pagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/fixed-cameras")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("mapId", mapId)
                .build(true)
                .toUri(), pageSize);
    }

    public List<Map<String, Object>> fixedCameras() {
        int pageSize = 100;
        return pagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/fixed-cameras")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .build(true)
                .toUri(), pageSize);
    }

    /** 查询 Control 汇总的当前用户固定摄像头健康状态。 */
    public Map<String, Object> fixedCameraHealth() {
        URI uri = uri(properties.getControlBaseUrl(), "/api/control/fixed-cameras/health")
                .build(true)
                .toUri();
        return responseMap(uri).orElse(Map.of("records", List.of()));
    }

    public List<Map<String, Object>> pathPoints(String pathId) {
        if (pathId == null || pathId.isBlank()) {
            return List.of();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/paths/" + pathId + "/points")
                .build(true)
                .toUri();
        return records(uri);
    }

    public List<Map<String, Object>> alarms() {
        return alarms(null, null, null);
    }

    public List<Map<String, Object>> alarms(String status, String occurredFrom, String occurredTo) {
        int pageSize = 100;
        return pagedRecords(pageNum -> {
            UriComponentsBuilder builder = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms")
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", pageSize);
            if (status != null && !status.isBlank()) {
                builder.queryParam("status", status);
            }
            if (occurredFrom != null && !occurredFrom.isBlank()) {
                builder.queryParam("occurredFrom", occurredFrom.replace(' ', 'T'));
            }
            if (occurredTo != null && !occurredTo.isBlank()) {
                builder.queryParam("occurredTo", occurredTo.replace(' ', 'T'));
            }
            return builder.build(true).toUri();
        }, pageSize);
    }

    public List<Map<String, Object>> actionableWorkflowAlarms() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/actionable-workflow")
                .build(true)
                .toUri();
        return records(uri);
    }

    public List<Map<String, Object>> alarmsForStatistics(String occurredFrom, String occurredTo) {
        return alarms(null, occurredFrom, occurredTo);
    }

    public boolean handleAlarm(String alarmId, String handleAction, String handleResult) {
        if (alarmId == null || alarmId.isBlank() || handleAction == null || handleAction.isBlank()) {
            return false;
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/" + alarmId + "/handled")
                .build(true)
                .toUri();
        return updateAlarm(uri, false, handleAction, handleResult);
    }

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
        if (handleResult != null && !handleResult.isBlank()) {
            body.put("handleResult", handleResult);
        }
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
            return true;
        } catch (RuntimeException exception) {
            log.warn("更新全景地图告警失败，请求地址={}", uri, exception);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(URI uri) {
        return responseMap(uri).map(this::records).orElse(List.of());
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
        return responseMap(uri)
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
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = generalRequestPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("全景通用查询并发已达上限，请求地址={}", uri);
                return Optional.empty();
            }
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            return response == null ? Optional.empty() : Optional.of(response);
        } catch (RuntimeException exception) {
            log.warn("请求全景地图中心端接口失败，请求地址={} 耗时毫秒={}", uri, elapsedMillis(startNanos), exception);
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (acquired) {
                generalRequestPermits.release();
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
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            List<Map<String, Object>> page = taskRecords(uriFactory.apply(pageNum), unavailableReasonCode);
            result.addAll(page);
            if (page.size() < pageSize) {
                return List.copyOf(result);
            }
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

    private Optional<Map<String, Object>> taskResponseMap(
            URI uri,
            String unavailableReasonCode,
            boolean notFoundAllowed) {
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = taskRequestPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new TaskSourceException("TASK_QUERY_CONCURRENCY_LIMIT", "任务查询并发已达上限");
            }
            Map<String, Object> response = taskRestClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                throw new TaskSourceException("TASK_INVALID_RESPONSE", "Management 任务查询返回空响应");
            }
            return Optional.of(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TaskSourceException("TASK_QUERY_INTERRUPTED", "任务查询等待被中断", exception);
        } catch (RestClientResponseException exception) {
            if (notFoundAllowed && exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("Management 任务引用已不存在，请求地址={}", uri);
                return Optional.empty();
            }
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new ResponseStatusException(exception.getStatusCode(), "查询 Management 任务权限失败", exception);
            }
            log.warn("Management 任务接口响应异常，请求地址={} 状态码={} 耗时毫秒={}",
                    uri, exception.getStatusCode().value(), elapsedMillis(startNanos));
            throw new TaskSourceException(unavailableReasonCode, "Management 任务接口响应异常", exception);
        } catch (ResourceAccessException exception) {
            log.warn("Management 任务接口连接或读取超时，请求地址={} 耗时毫秒={}",
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

    private Map<String, Object> requiredResponseMap(URI uri) {
        boolean acquired = false;
        long startNanos = System.nanoTime();
        try {
            acquired = generalRequestPermits.tryAcquire(100, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理端通用查询并发已达上限");
            }
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理端资源响应为空");
            }
            return response;
        } catch (RestClientResponseException exception) {
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

    public static final class TaskSourceException extends RuntimeException {

        private final String reasonCode;

        public TaskSourceException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public TaskSourceException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
