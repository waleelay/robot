package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import com.robot.bigscreen.config.WebSocketConfig;
import com.robot.bigscreen.panorama.StatsPart;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BigscreenWebSocketBridgeHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BigscreenWebSocketBridgeHandler.class);
    private static final long MAX_AUTHORIZATION_STALENESS_MS = 30000L;
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION,
            "X-User-Id",
            "X-Org-Id",
            "X-Roles",
            "X-Client-Id");
    private static final AtomicInteger AUTHORIZATION_THREAD_SEQUENCE = new AtomicInteger();

    private final CenterServiceProperties properties;
    private final PanoramaWebSocketEventAdapter eventAdapter;
    private final PanoramaLocationEventThrottler locationEventThrottler;
    private final PanoramaStatsEventRefresher statsEventRefresher;
    private final PanoramaTaskEventRefresher taskEventRefresher;
    private final PanoramaAlarmEventRefresher alarmEventRefresher;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final BigscreenWebSocketAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient;
    private final Map<String, WebSocketSession> centerSessions = new ConcurrentHashMap<>();
    private final Map<String, String> authorizationIdentityBySession = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationSnapshot> authorizationSnapshotsByIdentity = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> authorizationRefreshesByIdentity = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> browserSessions = ConcurrentHashMap.newKeySet();
    private final ExecutorService authorizationRefreshExecutor = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(
                runnable,
                "大屏权限刷新-" + AUTHORIZATION_THREAD_SEQUENCE.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    @Value("${bigscreen.websocket.authorization-max-staleness-ms:30000}")
    private long authorizationMaxStalenessMs = 30000L;

    public BigscreenWebSocketBridgeHandler(
            CenterServiceProperties properties,
            PanoramaWebSocketEventAdapter eventAdapter,
            PanoramaLocationEventThrottler locationEventThrottler,
            PanoramaStatsEventRefresher statsEventRefresher,
            PanoramaTaskEventRefresher taskEventRefresher,
            PanoramaAlarmEventRefresher alarmEventRefresher,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            BigscreenWebSocketAuthorizationService authorizationService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.eventAdapter = eventAdapter;
        this.locationEventThrottler = locationEventThrottler;
        this.statsEventRefresher = statsEventRefresher;
        this.taskEventRefresher = taskEventRefresher;
        this.alarmEventRefresher = alarmEventRefresher;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        container.setDefaultMaxBinaryMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        this.webSocketClient = new StandardWebSocketClient(container);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        if (tokenExpired(browserSession, Instant.now())) {
            closeForTokenExpiration(browserSession);
            return;
        }
        String identity = authorizationIdentity(browserSession);
        authorizationIdentityBySession.put(browserSession.getId(), identity);
        AuthorizationSnapshot snapshot = authorizationSnapshotsByIdentity.get(identity);
        try {
            if (snapshot == null || snapshot.expired(Instant.now())) {
                snapshot = loadAuthorizationSnapshot(browserSession);
                authorizationSnapshotsByIdentity.put(identity, snapshot);
            }
        } catch (RuntimeException exception) {
            authorizationIdentityBySession.remove(browserSession.getId());
            log.warn("大屏 WebSocket 初始权限加载失败，会话={}", browserSession.getId(), exception);
            if (credentialRejected(exception)) {
                closeForTokenExpiration(browserSession);
            } else {
                browserSession.close(new CloseStatus(4003, "权限加载失败"));
            }
            return;
        }
        log.debug("大屏 WebSocket 会话授权资源加载完成，会话={} 设备数={} 固定摄像头数={}",
                browserSession.getId(), snapshot.resources().robotIds().size(), snapshot.resources().cameraIds().size());
        browserSessions.add(browserSession);
        try {
            connectCenter(browserSession);
        } catch (Exception exception) {
            log.warn("中心端 WebSocket 不可用，浏览器会话将无法接收中心端实时事件，会话={}",
                    browserSession.getId(), exception);
        }
    }

    void connectCenter(WebSocketSession browserSession) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        copyHandshakeHeaders(browserSession, headers);
        WebSocketHandler centerHandler = new CenterToBrowserHandler(browserSession);
        WebSocketSession centerSession = webSocketClient
                .execute(centerHandler, headers, centerUri(browserSession))
                .get();
        centerSessions.put(browserSession.getId(), centerSession);
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
        AuthorizationSnapshot snapshot = validSnapshot(browserSession);
        if (snapshot == null) {
            return;
        }
        if (!authorizationService.canForwardClientMessage(snapshot.resources(), message.getPayload())) {
            log.warn("已拒绝无权限资源的 WebSocket 上行消息，会话={} 快照年龄毫秒={}",
                    browserSession.getId(), snapshot.ageMillis(Instant.now()));
            sendAuthorizationRejected(browserSession, message.getPayload());
            return;
        }
        WebSocketSession centerSession = centerSessions.get(browserSession.getId());
        if (centerSession != null && centerSession.isOpen()) {
            centerSession.sendMessage(message);
        } else {
            log.debug("中心端 WebSocket 不可用，已丢弃浏览器消息，会话={}",
                    browserSession.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) throws Exception {
        logClose("浏览器", browserSession, status);
        browserSessions.remove(browserSession);
        String identity = authorizationIdentityBySession.remove(browserSession.getId());
        removeUnusedIdentity(identity);
        eventAdapter.removeSession(browserSession.getId());
        locationEventThrottler.remove(browserSession.getId());
        statsEventRefresher.remove(browserSession.getId());
        taskEventRefresher.remove(browserSession.getId());
        alarmEventRefresher.remove(browserSession.getId());
        WebSocketSession centerSession = centerSessions.remove(browserSession.getId());
        if (centerSession != null && centerSession.isOpen()) {
            centerSession.close(status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) throws Exception {
        log.warn("浏览器 WebSocket 传输异常，会话={}", browserSession.getId(), exception);
        afterConnectionClosed(browserSession, CloseStatus.SERVER_ERROR);
    }

    public void broadcastToBrowserSessions(String payload) {
        for (WebSocketSession browserSession : browserSessions) {
            if (!browserSession.isOpen()) {
                browserSessions.remove(browserSession);
                continue;
            }
            try {
                sendToBrowserSession(browserSession, payload);
            } catch (Exception exception) {
                log.warn("向浏览器广播事件失败，会话={}", browserSession.getId(), exception);
            }
        }
    }

    /**
     * 检查 Token 和授权快照有效期，并按身份异步刷新。
     *
     * <p>检查线程不执行 Management 阻塞请求，避免一个慢会话拖住其他会话。快照在有效期
     * 过半后提前刷新，到达硬过期时间仍未成功刷新时立即关闭对应连接。</p>
     */
    @Scheduled(fixedDelayString = "${bigscreen.websocket.authorization-check-interval-ms:1000}")
    void refreshSessionAuthorizations() {
        Instant now = Instant.now();
        Set<String> refreshIdentities = ConcurrentHashMap.newKeySet();
        for (WebSocketSession browserSession : browserSessions) {
            if (!browserSession.isOpen()) {
                browserSessions.remove(browserSession);
                String identity = authorizationIdentityBySession.remove(browserSession.getId());
                removeUnusedIdentity(identity);
                continue;
            }
            if (tokenExpired(browserSession, now)) {
                closeForTokenExpiration(browserSession);
                continue;
            }
            String identity = authorizationIdentityBySession.get(browserSession.getId());
            AuthorizationSnapshot snapshot = identity == null
                    ? null
                    : authorizationSnapshotsByIdentity.get(identity);
            if (snapshot == null || snapshot.expired(now)) {
                log.warn("大屏 WebSocket 授权快照已过期，关闭会话={} 快照年龄毫秒={}",
                        browserSession.getId(), snapshot == null ? -1 : snapshot.ageMillis(now));
                closeForAuthorizationFailure(browserSession);
                continue;
            }
            if (snapshot.shouldRefresh(now, authorizationTtlMs()) && refreshIdentities.add(identity)) {
                WebSocketSession refreshSession = newestTokenSession(identity);
                if (refreshSession != null) {
                    refreshAuthorizationAsync(identity, refreshSession);
                }
            }
        }
    }

    private void refreshAuthorizationAsync(String identity, WebSocketSession browserSession) {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        if (authorizationRefreshesByIdentity.putIfAbsent(identity, pending) != null) {
            return;
        }
        authorizationRefreshExecutor.execute(() -> {
            try {
                AuthorizationSnapshot snapshot = loadAuthorizationSnapshot(browserSession);
                if (sessionsForIdentity(identity).isEmpty()) {
                    authorizationSnapshotsByIdentity.remove(identity);
                    return;
                }
                AuthorizationSnapshot previous = authorizationSnapshotsByIdentity.put(identity, snapshot);
                log.debug("大屏 WebSocket 权限刷新成功，身份={} 设备数={} 固定摄像头数={}",
                        identity, snapshot.resources().robotIds().size(), snapshot.resources().cameraIds().size());
                if (previous != null && !previous.resources().equals(snapshot.resources())) {
                    notifyAuthorizationChanged(identity);
                }
            } catch (RuntimeException exception) {
                if (tokenExpired(browserSession, Instant.now()) || credentialRejected(exception)) {
                    log.info("大屏 WebSocket Token 已失效，按登录凭证失效关闭会话，身份={}", identity);
                    sessionsForIdentity(identity).forEach(this::closeForTokenExpiration);
                } else {
                    log.warn("刷新大屏 WebSocket 权限失败，保留未过期快照并继续重试，身份={}",
                            identity, exception);
                }
            } finally {
                authorizationRefreshesByIdentity.remove(identity, pending);
                pending.complete(null);
            }
        });
    }

    void copyHandshakeHeaders(WebSocketSession browserSession, WebSocketHttpHeaders headers) {
        HttpHeaders source = browserSession.getHandshakeHeaders();
        for (String name : FORWARDED_HEADERS) {
            List<String> values = source.get(name);
            if (values != null && !values.isEmpty()) {
                headers.put(name, values);
            }
        }
        headers.putIfAbsent(
                "X-Client-Id",
                Collections.singletonList(clientIdFromQuery(browserSession)));
        String accessToken = queryParameter(browserSession, "access_token");
        if (headers.getFirst(HttpHeaders.AUTHORIZATION) == null && accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        if (browserSession.getPrincipal() instanceof Authentication authentication) {
            authenticatedRequestHeaders.apply(headers, authentication);
        }
    }

    private String clientIdFromQuery(WebSocketSession browserSession) {
        String clientId = queryParameter(browserSession, "clientId");
        return clientId == null ? browserSession.getId() : clientId;
    }

    URI centerUri(WebSocketSession browserSession) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getWebsocketControlUrl());
        String clientId = queryParameter(browserSession, "clientId");
        String accessToken = queryParameter(browserSession, "access_token");
        if (clientId != null) {
            builder.replaceQueryParam("clientId", clientId);
        }
        if (accessToken != null) {
            builder.replaceQueryParam("access_token", accessToken);
        }
        return builder.build(true).toUri();
    }

    private String queryParameter(WebSocketSession browserSession, String name) {
        URI uri = browserSession.getUri();
        if (uri == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(name);
        return value == null || value.isBlank() ? null : value;
    }

    private class CenterToBrowserHandler extends TextWebSocketHandler {

        private final WebSocketSession browserSession;

        CenterToBrowserHandler(WebSocketSession browserSession) {
            this.browserSession = browserSession;
        }

        @Override
        protected void handleTextMessage(WebSocketSession centerSession, TextMessage message) throws Exception {
            if (browserSession.isOpen()) {
                String centerPayload = message.getPayload();
                Set<StatsPart> statsParts = eventAdapter.statsRefreshParts(browserSession.getId(), centerPayload);
                boolean refreshTasks = eventAdapter.isTaskInvalidation(centerPayload);
                boolean refreshAlarms = eventAdapter.isAlarmInvalidation(centerPayload);
                boolean refreshFixedCameraHealth = eventAdapter.isFixedCameraHealthInvalidation(centerPayload);
                for (String payload : eventAdapter.adapt(centerPayload)) {
                    locationEventThrottler.publish(
                            browserSession.getId(),
                            payload,
                            value -> sendToBrowserSession(browserSession, value));
                }
                if (!statsParts.isEmpty()) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    statsEventRefresher.requestRefresh(
                            browserSession.getId(),
                            authentication,
                            payload -> sendUserScopedToBrowserSession(browserSession, payload),
                            statsParts);
                }
                if (refreshTasks) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    taskEventRefresher.requestRefresh(
                            browserSession.getId(),
                            authentication,
                            payload -> sendUserScopedToBrowserSession(browserSession, payload));
                }
                if (refreshAlarms) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    alarmEventRefresher.requestRefresh(
                            browserSession.getId(),
                            authentication,
                            payload -> sendUserScopedToBrowserSession(browserSession, payload));
                }
                if (refreshFixedCameraHealth) {
                    sendUserScopedToBrowserSession(
                            browserSession,
                            eventAdapter.fixedCameraHealthInvalidation(centerPayload));
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession centerSession, CloseStatus status) throws Exception {
            logClose("中心端", centerSession, status);
            centerSessions.remove(browserSession.getId());
            if (browserSession.isOpen()) {
                browserSession.close(status);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession centerSession, Throwable exception) throws Exception {
            log.warn("中心端 WebSocket 传输异常，浏览器会话={}", browserSession.getId(), exception);
            afterConnectionClosed(centerSession, CloseStatus.SERVER_ERROR);
        }
    }

    private void sendToBrowserSession(WebSocketSession browserSession, String payload) {
        if (!browserSession.isOpen()) {
            return;
        }
        AuthorizationSnapshot snapshot = validSnapshot(browserSession);
        if (snapshot == null) {
            return;
        }
        if (!authorizationService.canReceive(snapshot.resources(), payload)) {
            log.debug("已过滤无权限设备 WebSocket 事件，会话={}", browserSession.getId());
            return;
        }
        try {
            sendText(browserSession, payload);
        } catch (Exception exception) {
            log.warn("向浏览器发送事件失败，会话={}", browserSession.getId(), exception);
        }
    }

    private void sendUserScopedToBrowserSession(WebSocketSession browserSession, String payload) {
        if (!browserSession.isOpen()) {
            return;
        }
        if (validSnapshot(browserSession) == null) {
            return;
        }
        try {
            sendText(browserSession, payload);
        } catch (Exception exception) {
            log.warn("向浏览器发送用户范围事件失败，会话={}", browserSession.getId(), exception);
        }
    }

    private void sendText(WebSocketSession browserSession, String payload) throws Exception {
        synchronized (browserSession) {
            browserSession.sendMessage(new TextMessage(payload));
        }
    }

    private AuthorizationSnapshot validSnapshot(WebSocketSession browserSession) {
        Instant now = Instant.now();
        if (tokenExpired(browserSession, now)) {
            closeForTokenExpiration(browserSession);
            return null;
        }
        String identity = authorizationIdentityBySession.get(browserSession.getId());
        AuthorizationSnapshot snapshot = identity == null
                ? null
                : authorizationSnapshotsByIdentity.get(identity);
        if (snapshot == null || snapshot.expired(now)) {
            log.warn("拒绝使用过期的 WebSocket 授权快照，会话={} 快照年龄毫秒={}",
                    browserSession.getId(), snapshot == null ? -1 : snapshot.ageMillis(now));
            closeForAuthorizationFailure(browserSession);
            return null;
        }
        return snapshot;
    }

    private AuthorizationSnapshot loadAuthorizationSnapshot(WebSocketSession browserSession) {
        Instant loadedAt = Instant.now();
        BigscreenWebSocketAuthorizationService.AuthorizedResources resources =
                authorizationService.authorizedResources(browserSession);
        return new AuthorizationSnapshot(
                resources,
                loadedAt,
                loadedAt.plusMillis(authorizationTtlMs()));
    }

    private long authorizationTtlMs() {
        return Math.min(MAX_AUTHORIZATION_STALENESS_MS, Math.max(1L, authorizationMaxStalenessMs));
    }

    private String authorizationIdentity(WebSocketSession browserSession) {
        if (browserSession.getPrincipal() instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            String orgId = firstClaim(jwt, "org_id", "orgId", "organization_id", "tenant_id");
            return issuer + "|" + jwt.getSubject() + "|" + (orgId == null ? "" : orgId);
        }
        if (browserSession.getPrincipal() instanceof Authentication authentication) {
            return "principal|" + authentication.getName();
        }
        return "session|" + browserSession.getId();
    }

    private String firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            Object value = jwt.getClaim(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private boolean tokenExpired(WebSocketSession browserSession, Instant now) {
        if (!(browserSession.getPrincipal() instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }
        Instant expiresAt = jwtAuthentication.getToken().getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * Management 明确返回 401 时，以对端鉴权结果为准。
     *
     * <p>这既覆盖正常 Token 到期，也避免 BFF 主机时钟落后于认证服务时，
     * 把凭证失效误报成权限服务不可用。</p>
     */
    private boolean credentialRejected(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == 401) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private WebSocketSession newestTokenSession(String identity) {
        return sessionsForIdentity(identity).stream()
                .filter(WebSocketSession::isOpen)
                .max((left, right) -> tokenExpiry(left).compareTo(tokenExpiry(right)))
                .orElse(null);
    }

    private Instant tokenExpiry(WebSocketSession session) {
        if (session.getPrincipal() instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.getToken().getExpiresAt() != null) {
            return jwtAuthentication.getToken().getExpiresAt();
        }
        return Instant.MAX;
    }

    private List<WebSocketSession> sessionsForIdentity(String identity) {
        if (identity == null) {
            return List.of();
        }
        return browserSessions.stream()
                .filter(session -> identity.equals(authorizationIdentityBySession.get(session.getId())))
                .toList();
    }

    private void removeUnusedIdentity(String identity) {
        if (identity == null || !sessionsForIdentity(identity).isEmpty()) {
            return;
        }
        authorizationSnapshotsByIdentity.remove(identity);
    }

    private void sendAuthorizationRejected(WebSocketSession browserSession, String payload) {
        try {
            JsonNode incoming = objectMapper.readTree(payload);
            String type = incoming.path("type").asText("");
            String responseType = type.startsWith("video.intercom.call.")
                    ? "video.intercom.call.operation-failed"
                    : "control.command.rejected";
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", responseType);
            response.put("requestId", incoming.path("requestId").asText(""));
            response.put("timestamp", Instant.now().toString());
            response.put("payload", Map.of(
                    "code", "RESOURCE_FORBIDDEN",
                    "message", "当前用户无权操作目标资源"));
            sendText(browserSession, objectMapper.writeValueAsString(response));
        } catch (Exception exception) {
            log.warn("发送 WebSocket 越权拒绝回执失败，会话={}", browserSession.getId(), exception);
        }
    }

    private void notifyAuthorizationChanged(String identity) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "event", "bigscreen.authorization.changed",
                    "timestamp", Instant.now().toString(),
                    "data", Map.of("reason", "AUTHORIZED_RESOURCES_CHANGED")));
            sessionsForIdentity(identity).forEach(session -> sendUserScopedToBrowserSession(session, payload));
        } catch (Exception exception) {
            log.warn("发送大屏授权资源变更通知失败，身份={}", identity, exception);
        }
    }

    private void closeForAuthorizationFailure(WebSocketSession browserSession) {
        try {
            browserSession.close(new CloseStatus(4003, "权限刷新失败"));
        } catch (Exception closeException) {
            log.debug("关闭权限失效的大屏 WebSocket 会话失败，会话={}", browserSession.getId(), closeException);
        }
    }

    private void closeForTokenExpiration(WebSocketSession browserSession) {
        try {
            browserSession.close(new CloseStatus(4001, "Token 已过期"));
        } catch (Exception closeException) {
            log.debug("关闭 Token 已过期的大屏 WebSocket 会话失败，会话={}",
                    browserSession.getId(), closeException);
        }
    }

    @PreDestroy
    void shutdownAuthorizationRefreshExecutor() {
        authorizationRefreshExecutor.shutdownNow();
    }

    private void logClose(String side, WebSocketSession session, CloseStatus status) {
        if (CloseStatus.NORMAL.equals(status) || CloseStatus.GOING_AWAY.equals(status)) {
            log.debug("{} WebSocket 已关闭，会话={} 状态={}", side, session.getId(), status);
            return;
        }
        log.warn("{} WebSocket 异常关闭，会话={} 状态={}", side, session.getId(), status);
    }

    private record AuthorizationSnapshot(
            BigscreenWebSocketAuthorizationService.AuthorizedResources resources,
            Instant loadedAt,
            Instant expiresAt) {

        boolean expired(Instant now) {
            return !expiresAt.isAfter(now);
        }

        boolean shouldRefresh(Instant now, long maxStalenessMs) {
            long refreshAfterMs = Math.max(1L, maxStalenessMs / 2L);
            return !loadedAt.plusMillis(refreshAfterMs).isAfter(now);
        }

        long ageMillis(Instant now) {
            return Math.max(0L, Duration.between(loadedAt, now).toMillis());
        }
    }
}
