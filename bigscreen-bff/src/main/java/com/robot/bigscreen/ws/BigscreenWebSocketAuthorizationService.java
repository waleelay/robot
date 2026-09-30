package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.DownstreamServiceProperties;
import com.robot.bigscreen.fixedcamera.FixedCameraCatalogLeaseClient;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

/** 查询和缓存用户可见设备范围，过滤实时通道中的资源事件。 */
@Component
public class BigscreenWebSocketAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(BigscreenWebSocketAuthorizationService.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};
    private static final Set<String> USER_SCOPED_FIELD_CALL_EVENTS = Set.of(
            "video.field.call.incoming",
            "video.field.call.status");
    private static final Set<String> USER_SCOPED_CALL_TYPES = Set.of(
            "video.intercom.call.operation-failed",
            "video.field.call.list",
            "video.field.call.accepted",
            "video.field.call.rejected",
            "video.field.call.ended",
            "video.field.call.operation-failed");

    private final DownstreamServiceProperties properties;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final FixedCameraCatalogLeaseClient catalogLeaseClient;

    /**
     * 完整授权目录加载的最长毫秒数，机器人和摄像头查询共享同一截止时间。
     */
    @Value("${bigscreen.websocket.authorization-load-timeout-ms:8000}")
    private long authorizationLoadTimeoutMs = 8000L;

    /**
     * 初始化 BigscreenWebSocketAuthorizationService，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     * @param authenticatedRequestHeaders 根据已认证的用户与客户端身份生成可信下游请求头。
     * @param builder RestClient 构建器
     * @param objectMapper JSON 编解码器
     * @param catalogLeaseClient 将当前用户已获授权的固定摄像头配置以短租约同步给 Control。
     */
    public BigscreenWebSocketAuthorizationService(
            DownstreamServiceProperties properties,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            FixedCameraCatalogLeaseClient catalogLeaseClient) {
        this.properties = properties;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.objectMapper = objectMapper;
        this.catalogLeaseClient = catalogLeaseClient;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    /**
     * 按 WebSocket 当前认证上下文查询可访问机器人，查询失败不授予默认范围。
     *
     * @param session WebSocket 会话
     * @return 当前身份获准访问的机器人 ID 集合
     */
    public Set<String> authorizedRobotIds(WebSocketSession session) {
        return authorizedResources(session).robotIds();
    }

    /**
     * 查询当前身份可见的机器人及固定摄像头资源。
     *
     * @param session WebSocket 会话
     * @return 本次权威查询得到的授权资源集合
     */
    public AuthorizedResources authorizedResources(WebSocketSession session) {
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
            throw new IllegalStateException("大屏 WebSocket 会话缺少授权信息");
        }
        try {
            long deadlineNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(Math.max(1000L, authorizationLoadTimeoutMs));
            Set<String> robotIds = emptyWhenForbidden("设备", () -> loadAuthorizedIds(
                    headers,
                    "/api/v1/management/devices",
                    deadlineNanos,
                    "serialNumber", "robotId", "deviceCode", "id"), Set.of());
            List<Map<String, Object>> cameraRecords = emptyWhenForbidden("固定摄像头", () -> loadAuthorizedRecords(
                    headers,
                    "/api/v1/management/fixed-cameras",
                    deadlineNanos), List.of());
            Set<String> cameraIds = ids(cameraRecords, "cameraId", "id");
            catalogLeaseClient.synchronize(session.getPrincipal(), headers, cameraRecords);
            return new AuthorizedResources(robotIds, cameraIds);
        } catch (RuntimeException exception) {
            log.warn("加载大屏 WebSocket 授权资源失败，会话={}", session.getId(), exception);
            throw exception;
        }
    }

    <T> T emptyWhenForbidden(String resourceName, Supplier<T> loader, T emptyValue) {
        try {
            return loader.get();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 403) {
                throw exception;
            }
            log.info("当前用户的{}查看权限已撤销，授权集合按空集处理", resourceName);
            return emptyValue;
        }
    }

    /**
     * 按事件引用的资源集合过滤消息；明确的用户呼叫事件由专用分支放行，无资源标识事件按现有规则处理。
     * @param authorizedRobotIds 当前用户有权访问的机器人标识集合
     * @param payload 消息载荷
     * @return 当前连接或资源集合是否允许接收该事件
     */
    public boolean canReceive(Set<String> authorizedRobotIds, String payload) {
        return canReceive(new AuthorizedResources(
                authorizedRobotIds == null ? Set.of() : authorizedRobotIds,
                Set.of()), payload);
    }

    /**
     * 按事件引用的资源集合过滤消息；明确的用户呼叫事件由专用分支放行，无资源标识事件按现有规则处理。
     * @param resources 当前身份获准访问的机器人及摄像头集合
     * @param payload 消息载荷
     * @return 当前连接或资源集合是否允许接收该事件
     */
    public boolean canReceive(AuthorizedResources resources, String payload) {
        // 现场应用 呼叫使用 app-* / phone-camera 作为来电弹窗展示标识，
        // 它们不是 Management 设备资源，必须先按明确的呼叫消息类型放行。
        if (isUserScopedCallMessage(payload)) {
            return true;
        }
        Set<String> payloadRobotIds = robotIdsInPayload(payload);
        Set<String> payloadCameraIds = upstreamFixedCameraIdsInPayload(payload);
        if (payloadRobotIds.isEmpty() && payloadCameraIds.isEmpty()) {
            return false;
        }
        return resources != null
                && resources.robotIds().containsAll(payloadRobotIds)
                && resources.cameraIds().containsAll(payloadCameraIds);
    }

    private boolean isUserScopedCallMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("");
            String event = root.path("event").asText("");
            return USER_SCOPED_CALL_TYPES.contains(type)
                    || USER_SCOPED_FIELD_CALL_EVENTS.contains(event);
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 校验浏览器上行消息引用的资源是否属于当前授权集合。
     *
     *  <p>不包含资源标识的查询类消息继续交给 Control 做最终鉴权；只要消息显式携带
     *  robotId、cameraId 或固定摄像头 sourceId，BFF 就先拒绝明显越权的目标。</p>
     *
     * @param resources 当前身份获准访问的机器人及摄像头集合
     * @param payload 消息载荷
     * @return 当前身份是否允许将该控制或订阅消息发给下游
     */
    public boolean canForwardClientMessage(AuthorizedResources resources, String payload) {
        Set<String> payloadRobotIds = robotIdsInPayload(payload);
        Set<String> payloadCameraIds = cameraIdsInPayload(payload);
        if (payloadRobotIds.isEmpty() && payloadCameraIds.isEmpty()) {
            return true;
        }
        return resources != null
                && resources.robotIds().containsAll(payloadRobotIds)
                && resources.cameraIds().containsAll(payloadCameraIds);
    }

    Set<String> robotIdsInPayload(String payload) {
        return resourceIdsInPayload(payload, "robotId");
    }

    Set<String> cameraIdsInPayload(String payload) {
        Set<String> cameraIds = new HashSet<>(resourceIdsInPayload(payload, "cameraId"));
        if (payload == null || payload.isBlank()) {
            return Set.copyOf(cameraIds);
        }
        try {
            collectFixedCameraSourceIds(objectMapper.readTree(payload), cameraIds);
        } catch (Exception exception) {
            log.debug("解析 WebSocket 固定摄像头 sourceId 失败", exception);
        }
        return Set.copyOf(cameraIds);
    }

    /**
     * 提取上游事件直接引用的固定摄像头资源。
     *
     * <p>{@code robot.state} 中的 {@code cameras[].cameraId} 和上装状态里的同名字段
     * 都是机器人附属相机标识，继承顶层 {@code robotId} 权限，不能拿它们与 Management
     * 固定摄像头授权集合比较。其他事件继续兼容显式 {@code cameraId}；标准视频源结构则
     * 始终按 {@code sourceType=FIXED_CAMERA + sourceId} 识别。</p>
     */
    private Set<String> upstreamFixedCameraIdsInPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            Set<String> cameraIds = new HashSet<>();
            if (!"robot.state".equals(root.path("event").asText())) {
                collectResourceIds(root, "cameraId", cameraIds);
            }
            collectFixedCameraSourceIds(root, cameraIds);
            return Set.copyOf(cameraIds);
        } catch (Exception exception) {
            log.debug("解析 WebSocket 上游固定摄像头资源失败", exception);
            return Set.of();
        }
    }

    private Set<String> resourceIdsInPayload(String payload, String fieldName) {
        if (payload == null || payload.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            Set<String> robotIds = new HashSet<>();
            collectResourceIds(root, fieldName, robotIds);
            return robotIds;
        } catch (Exception exception) {
            log.debug("解析 WebSocket 事件 robotId 失败，按非设备事件处理", exception);
            return Set.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> records(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("Management 授权查询返回空响应");
        }
        Object data = response.get("data");
        Object records = data;
        if (data instanceof Map<?, ?> dataMap) {
            records = dataMap.get("records");
        }
        if (!(records instanceof List<?> list)) {
            throw new IllegalStateException("Management 授权查询响应缺少 records 数组");
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private Set<String> loadAuthorizedIds(
            HttpHeaders headers,
            String path,
            long deadlineNanos,
            String... idFields) {
        int pageSize = 500;
        Set<String> ids = new HashSet<>();
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            requireBeforeDeadline(deadlineNanos, path);
            URI uri = UriComponentsBuilder.fromUriString(baseUrl() + path)
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", pageSize)
                    .build(true)
                    .toUri();
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(target -> target.addAll(headers))
                    .retrieve()
                    .body(MAP_TYPE);
            requireBeforeDeadline(deadlineNanos, path);
            List<Map<String, Object>> records = records(response);
            for (Map<String, Object> item : records) {
                firstString(item, idFields).ifPresent(ids::add);
            }
            if (records.size() < pageSize) {
                return Set.copyOf(ids);
            }
        }
        throw new IllegalStateException("Management 授权资源分页超过安全上限：" + path);
    }

    private List<Map<String, Object>> loadAuthorizedRecords(
            HttpHeaders headers,
            String path,
            long deadlineNanos) {
        int pageSize = 500;
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (int pageNum = 1; pageNum <= 1000; pageNum++) {
            requireBeforeDeadline(deadlineNanos, path);
            URI uri = UriComponentsBuilder.fromUriString(baseUrl() + path)
                    .queryParam("pageNum", pageNum)
                    .queryParam("pageSize", pageSize)
                    .build(true)
                    .toUri();
            Map<String, Object> response = restClient.get()
                    .uri(uri)
                    .headers(target -> target.addAll(headers))
                    .retrieve()
                    .body(MAP_TYPE);
            requireBeforeDeadline(deadlineNanos, path);
            List<Map<String, Object>> page = records(response);
            result.addAll(page);
            if (page.size() < pageSize) {
                return List.copyOf(result);
            }
        }
        throw new IllegalStateException("Management 授权资源分页超过安全上限：" + path);
    }

    private Set<String> ids(List<Map<String, Object>> records, String... idFields) {
        Set<String> ids = new HashSet<>();
        records.forEach(item -> firstString(item, idFields).ifPresent(ids::add));
        return Set.copyOf(ids);
    }

    private void requireBeforeDeadline(long deadlineNanos, String path) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new IllegalStateException("Management 授权查询超过总时限：" + path);
        }
    }

    private void collectResourceIds(JsonNode node, String fieldName, Set<String> resourceIds) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode resourceId = node.get(fieldName);
            if (resourceId != null && resourceId.isValueNode()) {
                String value = resourceId.asText();
                if (value != null && !value.isBlank()) {
                    resourceIds.add(value);
                }
            }
            node.properties().forEach(entry -> collectResourceIds(entry.getValue(), fieldName, resourceIds));
        } else if (node.isArray()) {
            node.forEach(item -> collectResourceIds(item, fieldName, resourceIds));
        }
    }

    private void collectFixedCameraSourceIds(JsonNode node, Set<String> cameraIds) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode sourceType = node.get("sourceType");
            JsonNode sourceId = node.get("sourceId");
            if (sourceType != null
                    && "FIXED_CAMERA".equalsIgnoreCase(sourceType.asText())
                    && sourceId != null
                    && !sourceId.asText().isBlank()) {
                cameraIds.add(sourceId.asText());
            }
            node.properties().forEach(entry -> collectFixedCameraSourceIds(entry.getValue(), cameraIds));
        } else if (node.isArray()) {
            node.forEach(item -> collectFixedCameraSourceIds(item, cameraIds));
        }
    }

    /**
     * 当前用户授权的机器人及摄像头集合，构造时复制避免外部修改。
     *
     * @param robotIds 机器人标识集合
     * @param cameraIds 固定摄像头 ID 列表
     */
    public record AuthorizedResources(Set<String> robotIds, Set<String> cameraIds) {

        /**
         * 初始化 AuthorizedResources，保存所需依赖及初始运行状态。
         *
         * @param robotIds 机器人标识集合
         * @param cameraIds 固定摄像头 ID 列表
         */
        public AuthorizedResources {
            robotIds = robotIds == null ? Set.of() : Set.copyOf(robotIds);
            cameraIds = cameraIds == null ? Set.of() : Set.copyOf(cameraIds);
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
