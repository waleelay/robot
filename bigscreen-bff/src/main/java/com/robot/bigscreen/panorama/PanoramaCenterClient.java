package com.robot.bigscreen.panorama;

import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PanoramaCenterClient {

    private static final Logger log = LoggerFactory.getLogger(PanoramaCenterClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final CenterServiceProperties properties;

    public PanoramaCenterClient(RestClient.Builder builder, CenterServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
    }

    public List<Map<String, Object>> devices() {
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/devices")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 100)
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
                .queryParam("includeRunning", true)
                .build(true)
                .toUri();
        return records(uri);
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
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 20)
                .build(true)
                .toUri();
        return records(uri);
    }

    public boolean handleAlarm(String alarmId, String handleResult) {
        if (alarmId == null || alarmId.isBlank() || handleResult == null || handleResult.isBlank()) {
            return false;
        }
        URI uri = uri(properties.getManageBaseUrl(), "/api/v1/management/alarms/" + alarmId + "/handled")
                .build(true)
                .toUri();
        try {
            restClient.patch()
                    .uri(uri)
                    .body(Map.of("handledBy", "bigscreen", "handleResult", handleResult))
                    .retrieve()
                    .body(MAP_TYPE);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Failed to handle panorama alarm alarmId={} uri={}", alarmId, uri, exception);
            return false;
        }
    }

    private List<Map<String, Object>> records(URI uri) {
        return dataMap(uri)
                .map(data -> {
                    Object records = data.get("records");
                    if (records instanceof List<?> list) {
                        return maps(list);
                    }
                    return List.<Map<String, Object>>of(data);
                })
                .orElseGet(() -> listData(uri));
    }

    private List<Map<String, Object>> listData(URI uri) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(MAP_TYPE);
            Object data = response == null ? null : response.get("data");
            if (data instanceof List<?> list) {
                return maps(list);
            }
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("Failed to request panorama center list uri={}", uri, exception);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> dataMap(URI uri) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null) {
                return Optional.empty();
            }
            Object data = response.get("data");
            if (data instanceof Map<?, ?> map) {
                return Optional.of((Map<String, Object>) map);
            }
            if (!response.containsKey("code") && !response.containsKey("data")) {
                return Optional.of(response);
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Failed to request panorama center data uri={}", uri, exception);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(List<?> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private UriComponentsBuilder uri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8088" : baseUrl;
        String separator = normalizedBaseUrl.endsWith("/") || path.startsWith("/") ? "" : "/";
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl + separator + path);
    }
}
