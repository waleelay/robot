package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BigscreenWebSocketAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(BigscreenWebSocketAuthorizationService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final CenterServiceProperties properties;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BigscreenWebSocketAuthorizationService(
            CenterServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            RestClient.Builder builder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    public Set<String> authorizedRobotIds(WebSocketSession session) {
        HttpHeaders headers = new HttpHeaders();
        if (session.getPrincipal() instanceof Authentication authentication) {
            authenticatedRequestHeaders.apply(headers, authentication);
        } else {
            String accessToken = queryParameter(session, "access_token");
            if (accessToken != null) {
                headers.setBearerAuth(accessToken);
            }
        }
        if (headers.getFirst(HttpHeaders.AUTHORIZATION) == null) {
            log.warn("大屏 WebSocket 会话缺少授权信息，已按空设备权限处理，会话={}", session.getId());
            return Set.of();
        }

        URI uri = UriComponentsBuilder.fromUriString(baseUrl() + "/api/v1/management/devices")
                .queryParam("pageNum", 1)
                .queryParam("pageSize", 500)
                .build(true)
                .toUri();
        try {
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(target -> target.addAll(headers))
                    .retrieve()
                    .body(MAP_TYPE);
            return robotIdsFromDeviceRecords(response);
        } catch (RuntimeException exception) {
            log.warn("加载大屏 WebSocket 授权设备列表失败，会话={} 请求地址={}", session.getId(), uri, exception);
            return Set.of();
        }
    }

    public boolean canReceive(Set<String> authorizedRobotIds, String payload) {
        Set<String> payloadRobotIds = robotIdsInPayload(payload);
        if (payloadRobotIds.isEmpty()) {
            return true;
        }
        return authorizedRobotIds != null && authorizedRobotIds.containsAll(payloadRobotIds);
    }

    Set<String> robotIdsInPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            Set<String> robotIds = new HashSet<>();
            collectRobotIds(root, robotIds);
            return robotIds;
        } catch (Exception exception) {
            log.debug("解析 WebSocket 事件 robotId 失败，按非设备事件处理", exception);
            return Set.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> robotIdsFromDeviceRecords(Map<String, Object> response) {
        if (response == null) {
            return Set.of();
        }
        Object data = response.get("data");
        Object records = data;
        if (data instanceof Map<?, ?> dataMap) {
            records = dataMap.get("records");
        }
        if (!(records instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> robotIds = new HashSet<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                firstString((Map<String, Object>) map, "serialNumber", "robotId", "deviceCode", "id")
                        .ifPresent(robotIds::add);
            }
        }
        return Set.copyOf(robotIds);
    }

    private void collectRobotIds(JsonNode node, Set<String> robotIds) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode robotId = node.get("robotId");
            if (robotId != null && robotId.isValueNode()) {
                String value = robotId.asText();
                if (value != null && !value.isBlank()) {
                    robotIds.add(value);
                }
            }
            node.fields().forEachRemaining(entry -> collectRobotIds(entry.getValue(), robotIds));
        } else if (node.isArray()) {
            node.forEach(item -> collectRobotIds(item, robotIds));
        }
    }

    private Optional<String> firstString(Map<String, Object> source, String... names) {
        for (String name : names) {
            Object value = source.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return Optional.of(String.valueOf(value));
            }
        }
        return Optional.empty();
    }

    private String queryParameter(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
        return value == null || value.isBlank() ? null : value;
    }

    private String baseUrl() {
        String baseUrl = properties.getManageBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8088";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
