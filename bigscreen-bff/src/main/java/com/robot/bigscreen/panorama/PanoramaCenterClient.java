package com.robot.bigscreen.panorama;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PanoramaCenterClient {

    private static final Logger log = LoggerFactory.getLogger(PanoramaCenterClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CenterServiceProperties properties;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;

    public PanoramaCenterClient(
            RestClient.Builder builder,
            CenterServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
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
        return records(uri);
    }

    public Optional<Map<String, Object>> taskWorkflowDefinition(String workflowDefinitionId) {
        if (workflowDefinitionId == null || workflowDefinitionId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-definitions/" + workflowDefinitionId)
                .build(true)
                .toUri();
        return dataMap(uri);
    }

    public List<Map<String, Object>> taskWorkflowInstances() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 100)
                .queryParam("scope", "ALL")
                .build(true)
                .toUri();
        return records(uri);
    }

    public List<Map<String, Object>> taskWorkflowInstancesForStatistics() {
        int pageSize = 100;
        return pagedRecords(pageNum -> uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances")
                .queryParam("pageNum", pageNum)
                .queryParam("pageSize", pageSize)
                .queryParam("scope", "ALL")
                .build(true)
                .toUri(), pageSize);
    }

    public Optional<Map<String, Object>> taskWorkflowInstance(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId)
                .build(true)
                .toUri();
        return dataMap(uri);
    }

    public Optional<Map<String, Object>> taskWorkflowReplay(String workflowInstanceId) {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            return Optional.empty();
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/task-workflow-instances/" + workflowInstanceId + "/replay")
                .build(true)
                .toUri();
        return dataMap(uri);
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
        return records(uri);
    }

    public List<Map<String, Object>> enabledMaps() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/maps")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .queryParam("enabled", true)
                .build(true)
                .toUri();
        return records(uri);
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
        long startNanos = System.nanoTime();
        try {
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
        }
    }

    private List<Map<String, Object>> pagedRecords(IntFunction<URI> uriFactory, int pageSize) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            List<Map<String, Object>> page = records(uriFactory.apply(pageNum));
            result.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> requiredPagedRecords(IntFunction<URI> uriFactory, int pageSize) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            URI uri = uriFactory.apply(pageNum);
            List<Map<String, Object>> page = records(requiredResponseMap(uri));
            result.addAll(page);
            if (page.size() < pageSize) {
                return result;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "管理端设备分页超过安全上限");
    }

    private Map<String, Object> requiredResponseMap(URI uri) {
        long startNanos = System.nanoTime();
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(authenticatedRequestHeaders::apply)
                    .retrieve()
                    .body(MAP_TYPE);
            logSlowRequest(uri, startNanos);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理端设备响应为空");
            }
            return response;
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    ? HttpStatus.UNAUTHORIZED
                    : exception.getStatusCode() == HttpStatus.FORBIDDEN
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.SERVICE_UNAVAILABLE;
            throw new ResponseStatusException(status, "查询管理端授权设备失败", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "查询管理端授权设备失败",
                    exception);
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
}
