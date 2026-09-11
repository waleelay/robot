package com.robot.bigscreen.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import com.robot.bigscreen.config.WebSocketConfig;
import com.robot.bigscreen.fixedcamera.FixedCameraCatalogLeaseClient;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.support.TaskExecutorAdapter;
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
    private static final long MAX_AUTHORIZATION_STALENESS_MS = 300000L;
    private static final long MAX_INITIALIZATION_WAIT_MS = 30000L;
    private static final long CENTER_CONNECTION_TIMEOUT_MS = 10000L;
    private static final int AUTHORIZATION_REFRESH_THREADS = 16;
    private static final int AUTHORIZATION_REFRESH_QUEUE_CAPACITY = 64;
    private static final int CENTER_CONNECTION_THREADS = 16;
    private static final int CENTER_CONNECTION_QUEUE_CAPACITY = 64;
    private static final int MAX_IDENTITY_SESSION_QUOTA = 64;
    private static final int MAX_ORGANIZATION_SESSION_QUOTA = 4096;
    private static final int MAX_INSTANCE_SESSION_QUOTA = 4096;
    private static final Pattern ACCESS_TOKEN_LOG_VALUE =
            Pattern.compile("(?i)(access_token=)[^&\\s\\]]+");
    private static final Pattern BEARER_LOG_VALUE =
            Pattern.compile("(?i)(Bearer\\s+)[^\\s,;\\]]+");
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION,
            "X-User-Id",
            "X-Org-Id",
            "X-Roles",
            "X-Client-Id");
    private static final AtomicInteger AUTHORIZATION_THREAD_SEQUENCE = new AtomicInteger();
    private static final AtomicInteger CENTER_CONNECTION_THREAD_SEQUENCE = new AtomicInteger();

    private final CenterServiceProperties properties;
    private final PanoramaWebSocketEventAdapter eventAdapter;
    private final PanoramaLocationEventThrottler locationEventThrottler;
    private final PanoramaStatsEventRefresher statsEventRefresher;
    private final PanoramaTaskEventRefresher taskEventRefresher;
    private final PanoramaAlarmEventRefresher alarmEventRefresher;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final BigscreenWebSocketAuthorizationService authorizationService;
    private final FixedCameraCatalogLeaseClient catalogLeaseClient;
    private final ObjectMapper objectMapper;
    private final StandardWebSocketClient webSocketClient;
    private final ExecutorService centerConnectionExecutor;
    private final Map<String, WebSocketSession> centerSessions = new ConcurrentHashMap<>();
    private final Map<String, String> authorizationIdentityBySession = new ConcurrentHashMap<>();
    private final Map<String, String> authorizationOrganizationBySession = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationSnapshot> authorizationSnapshotsByIdentity = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<AuthorizationSnapshot>> authorizationInitialLoadsByIdentity =
            new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> authorizationRefreshesByIdentity = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationRetryState> authorizationRetryStates = new ConcurrentHashMap<>();
    private final Set<String> authorizationUnavailableIdentities = ConcurrentHashMap.newKeySet();
    private final Set<WebSocketSession> browserSessions = ConcurrentHashMap.newKeySet();
    private final Set<WebSocketSession> fieldCallSessions = ConcurrentHashMap.newKeySet();
    private final Object sessionQuotaMonitor = new Object();
    private final ExecutorService authorizationRefreshExecutor = boundedExecutor(
            AUTHORIZATION_REFRESH_THREADS,
            AUTHORIZATION_REFRESH_QUEUE_CAPACITY,
            "大屏权限刷新-",
            AUTHORIZATION_THREAD_SEQUENCE);

    @Value("${bigscreen.websocket.authorization-max-staleness-ms:300000}")
    private long authorizationMaxStalenessMs = 300000L;

    @Value("${bigscreen.websocket.initialization-wait-ms:15000}")
    private long initializationWaitMs = 15000L;

    @Value("${bigscreen.websocket.max-sessions-per-identity:8}")
    private int maxSessionsPerIdentity = 8;

    @Value("${bigscreen.websocket.max-sessions-per-organization:64}")
    private int maxSessionsPerOrganization = 64;

    @Value("${bigscreen.websocket.max-sessions-per-instance:64}")
    private int maxSessionsPerInstance = 64;

    public BigscreenWebSocketBridgeHandler(
            CenterServiceProperties properties,
            PanoramaWebSocketEventAdapter eventAdapter,
            PanoramaLocationEventThrottler locationEventThrottler,
            PanoramaStatsEventRefresher statsEventRefresher,
            PanoramaTaskEventRefresher taskEventRefresher,
            PanoramaAlarmEventRefresher alarmEventRefresher,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            BigscreenWebSocketAuthorizationService authorizationService,
            FixedCameraCatalogLeaseClient catalogLeaseClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.eventAdapter = eventAdapter;
        this.locationEventThrottler = locationEventThrottler;
        this.statsEventRefresher = statsEventRefresher;
        this.taskEventRefresher = taskEventRefresher;
        this.alarmEventRefresher = alarmEventRefresher;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.authorizationService = authorizationService;
        this.catalogLeaseClient = catalogLeaseClient;
        this.objectMapper = objectMapper;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        container.setDefaultMaxBinaryMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        this.webSocketClient = new StandardWebSocketClient(container);
        this.centerConnectionExecutor = boundedExecutor(
                CENTER_CONNECTION_THREADS,
                CENTER_CONNECTION_QUEUE_CAPACITY,
                "大屏中心建连-",
                CENTER_CONNECTION_THREAD_SEQUENCE);
        this.webSocketClient.setTaskExecutor(new TaskExecutorAdapter(centerConnectionExecutor));
    }

    private static ExecutorService boundedExecutor(
            int threads,
            int queueCapacity,
            String threadPrefix,
            AtomicInteger sequence) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadPrefix + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        if (tokenExpired(browserSession, Instant.now())) {
            closeForTokenExpiration(browserSession);
            return;
        }
        String identity = authorizationIdentity(browserSession);
        if (!reserveSession(browserSession, identity)) {
            return;
        }
        if (isFieldCallSession(browserSession)) {
            fieldCallSessions.add(browserSession);
            try {
                connectCenter(browserSession);
            } catch (Exception exception) {
                log.warn("现场 App WebSocket 上游连接失败，会话={} 异常={} 原因={}",
                        browserSession.getId(), exception.getClass().getSimpleName(),
                        sanitizedConnectionFailureReason(exception));
                browserSession.close(CloseStatus.SERVER_ERROR);
            }
            return;
        }
        AuthorizationSnapshot snapshot;
        try {
            snapshot = initialAuthorizationSnapshot(identity, browserSession);
        } catch (RuntimeException exception) {
            releaseSessionReservation(browserSession.getId());
            removeUnusedIdentity(identity, browserSession);
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
        requestAlarmSnapshot(browserSession);
        try {
            connectCenter(browserSession);
            requestTaskRefresh(browserSession, false);
        } catch (Exception exception) {
            log.warn("中心端 WebSocket 连接失败，关闭浏览器会话以触发现有重连，会话={} 异常={} 原因={}",
                    browserSession.getId(), exception.getClass().getSimpleName(),
                    sanitizedConnectionFailureReason(exception));
            browserSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    static String sanitizedConnectionFailureReason(Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        if (message == null || message.isBlank()) {
            return "无详细信息";
        }
        String sanitized = ACCESS_TOKEN_LOG_VALUE.matcher(message).replaceAll("$1[已脱敏]");
        return BEARER_LOG_VALUE.matcher(sanitized).replaceAll("$1[已脱敏]");
    }

    void connectCenter(WebSocketSession browserSession) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        copyHandshakeHeaders(browserSession, headers);
        WebSocketHandler centerHandler = new CenterToBrowserHandler(browserSession);
        CompletableFuture<WebSocketSession> connection =
                webSocketClient.execute(centerHandler, headers, centerUri(browserSession));
        WebSocketSession centerSession;
        try {
            centerSession = connection.get(CENTER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            connection.cancel(true);
            throw new IllegalStateException("中心端 WebSocket 建连超时", exception);
        }
        if (!registerCenterSession(browserSession, centerSession) && centerSession.isOpen()) {
            centerSession.close(CloseStatus.GOING_AWAY);
        }
    }

    private boolean registerCenterSession(
            WebSocketSession browserSession,
            WebSocketSession centerSession) {
        synchronized (sessionQuotaMonitor) {
            if (!browserSession.isOpen()
                    || !authorizationIdentityBySession.containsKey(browserSession.getId())) {
                return false;
            }
            WebSocketSession existing = centerSessions.putIfAbsent(browserSession.getId(), centerSession);
            return existing == null || existing == centerSession;
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
        if (isFieldCallSession(browserSession)) {
            forwardToCenter(browserSession, message);
            return;
        }
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
        forwardToCenter(browserSession, message);
    }

    private void forwardToCenter(WebSocketSession browserSession, TextMessage message) throws Exception {
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
        cleanupBrowserSession(browserSession, status);
    }

    private void cleanupBrowserSession(WebSocketSession browserSession, CloseStatus status) {
        browserSessions.remove(browserSession);
        fieldCallSessions.remove(browserSession);
        String identity = releaseSessionReservation(browserSession.getId());
        eventAdapter.removeSession(browserSession.getId());
        locationEventThrottler.remove(browserSession.getId());
        WebSocketSession centerSession = centerSessions.remove(browserSession.getId());
        if (centerSession != null && centerSession.isOpen()) {
            try {
                centerSession.close(status);
            } catch (Exception exception) {
                log.debug("关闭中心端 WebSocket 失败，会话={}", browserSession.getId(), exception);
            }
        }
        removeUnusedRefreshState(identity);
        removeUnusedIdentity(identity, browserSession);
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) throws Exception {
        log.warn("浏览器 WebSocket 传输异常，会话={}", browserSession.getId(), exception);
        cleanupBrowserSession(browserSession, CloseStatus.SERVER_ERROR);
        if (browserSession.isOpen()) {
            browserSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcastToBrowserSessions(String payload) {
        for (WebSocketSession browserSession : browserSessions) {
            if (!browserSession.isOpen()) {
                cleanupBrowserSession(browserSession, CloseStatus.GOING_AWAY);
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
     * <p>检查线程不执行 Management 阻塞请求，避免一个慢会话拖住其他会话。快照提前分散刷新；
     * 到达硬过期时间仍未成功时保留连接但停止授权业务流量，避免断线重连放大。</p>
     */
    @Scheduled(fixedDelayString = "${bigscreen.websocket.authorization-check-interval-ms:1000}")
    void refreshSessionAuthorizations() {
        Instant now = Instant.now();
        for (WebSocketSession fieldCallSession : fieldCallSessions) {
            if (!fieldCallSession.isOpen()) {
                cleanupBrowserSession(fieldCallSession, CloseStatus.GOING_AWAY);
            } else if (tokenExpired(fieldCallSession, now)) {
                closeForTokenExpiration(fieldCallSession);
            }
        }
        Set<String> refreshIdentities = ConcurrentHashMap.newKeySet();
        for (WebSocketSession browserSession : browserSessions) {
            if (!browserSession.isOpen()) {
                cleanupBrowserSession(browserSession, CloseStatus.GOING_AWAY);
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
                markAuthorizationUnavailable(identity, snapshot, now);
                requestAuthorizationRefresh(identity, refreshIdentities);
                continue;
            }
            if (snapshot.shouldRefresh(now, authorizationRefreshAfterMs(identity, authorizationTtlMs()))) {
                requestAuthorizationRefresh(identity, refreshIdentities);
            }
        }
    }

    private void requestAuthorizationRefresh(String identity, Set<String> refreshIdentities) {
        if (identity == null || !refreshIdentities.add(identity) || !authorizationRetryDue(identity, Instant.now())) {
            return;
        }
        WebSocketSession refreshSession = newestTokenSession(identity);
        if (refreshSession != null) {
            refreshAuthorizationAsync(identity, refreshSession);
        }
    }

    private void refreshAuthorizationAsync(String identity, WebSocketSession browserSession) {
        if (identity == null || browserSession == null) {
            return;
        }
        CompletableFuture<Void> pending = new CompletableFuture<>();
        if (authorizationRefreshesByIdentity.putIfAbsent(identity, pending) != null) {
            return;
        }
        try {
            authorizationRefreshExecutor.execute(() -> {
                try {
                    AuthorizationSnapshot snapshot = loadAuthorizationSnapshot(browserSession);
                    if (sessionsForIdentity(identity).isEmpty()) {
                        authorizationSnapshotsByIdentity.remove(identity);
                        return;
                    }
                    AuthorizationSnapshot previous = authorizationSnapshotsByIdentity.put(identity, snapshot);
                    authorizationRetryStates.remove(identity);
                    boolean recovered = authorizationUnavailableIdentities.remove(identity);
                    log.debug("大屏 WebSocket 权限刷新成功，身份={} 设备数={} 固定摄像头数={}",
                            identity, snapshot.resources().robotIds().size(), snapshot.resources().cameraIds().size());
                    if (recovered) {
                        notifyAuthorizationAvailability(identity, true);
                    }
                    if (previous != null && !previous.resources().equals(snapshot.resources())) {
                        notifyAuthorizationChanged(identity);
                    }
                } catch (RuntimeException exception) {
                    if (tokenExpired(browserSession, Instant.now()) || credentialRejected(exception)) {
                        log.info("大屏 WebSocket Token 已失效，按登录凭证失效关闭会话，身份={}", identity);
                        sessionsForIdentity(identity).forEach(this::closeForTokenExpiration);
                    } else {
                        recordAuthorizationRefreshFailure(identity);
                        log.warn("刷新大屏 WebSocket 权限失败，保留连接并按 fail-closed 退避重试，身份={}",
                                identity, exception);
                    }
                } finally {
                    authorizationRefreshesByIdentity.remove(identity, pending);
                    pending.complete(null);
                }
            });
        } catch (RejectedExecutionException exception) {
            authorizationRefreshesByIdentity.remove(identity, pending);
            pending.completeExceptionally(exception);
            recordAuthorizationRefreshFailure(identity);
            log.warn("大屏 WebSocket 权限刷新队列已满，身份={}，保留连接并退避重试", identity);
        }
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
        String path = browserSession.getUri() == null ? "" : browserSession.getUri().getPath();
        String baseUrl = path != null && path.endsWith("/ws/field-call")
                ? properties.getWebsocketFieldCallUrl()
                : properties.getWebsocketControlUrl();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        String clientId = queryParameter(browserSession, "clientId");
        String accessToken = queryParameter(browserSession, "access_token");
        String displayName = queryParameter(browserSession, "displayName");
        if (clientId != null) {
            builder.replaceQueryParam("clientId", clientId);
        }
        if (accessToken != null) {
            builder.replaceQueryParam("access_token", accessToken);
        }
        if (displayName != null) {
            builder.replaceQueryParam("displayName", displayName);
        }
        return builder.build(true).toUri();
    }

    private boolean isFieldCallSession(WebSocketSession session) {
        return session.getUri() != null && session.getUri().getPath() != null
                && session.getUri().getPath().endsWith("/ws/field-call");
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
        public void afterConnectionEstablished(WebSocketSession centerSession) throws Exception {
            if (!registerCenterSession(browserSession, centerSession)) {
                centerSession.close(CloseStatus.GOING_AWAY);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession centerSession, TextMessage message) throws Exception {
            if (fieldCallSessions.contains(browserSession)) {
                if (browserSession.isOpen()) {
                    sendText(browserSession, message.getPayload());
                }
                return;
            }
            if (browserSession.isOpen() && validSnapshot(browserSession) != null) {
                String centerPayload = message.getPayload();
                Set<StatsPart> statsParts = eventAdapter.statsRefreshParts(browserSession.getId(), centerPayload);
                boolean refreshTasks = eventAdapter.isTaskInvalidation(centerPayload);
                boolean refreshAlarms = eventAdapter.isAlarmInvalidation(centerPayload);
                for (String payload : eventAdapter.adapt(centerPayload)) {
                    locationEventThrottler.publish(
                            browserSession.getId(),
                            payload,
                            value -> sendToBrowserSession(browserSession, value));
                }
                if (!statsParts.isEmpty()) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    String identity = authorizationIdentityBySession.getOrDefault(
                            browserSession.getId(), authorizationIdentity(browserSession));
                    statsEventRefresher.requestRefresh(
                            identity,
                            authentication,
                            payload -> sendUserScopedToIdentity(identity, payload),
                            statsParts);
                }
                if (refreshTasks) {
                    requestTaskRefresh(browserSession, true);
                }
                if (refreshAlarms) {
                    requestAlarmRefresh(browserSession);
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession centerSession, CloseStatus status) throws Exception {
            logClose("中心端", centerSession, status);
            centerSessions.remove(browserSession.getId(), centerSession);
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

    private void requestTaskRefresh(WebSocketSession browserSession, boolean followChanges) {
        Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
        String identity = authorizationIdentityBySession.getOrDefault(
                browserSession.getId(), authorizationIdentity(browserSession));
        taskEventRefresher.requestRefresh(identity, authentication,
                payload -> sendUserScopedToIdentity(identity, payload), followChanges);
    }

    private void requestAlarmRefresh(WebSocketSession browserSession) {
        Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
        String identity = authorizationIdentityBySession.getOrDefault(
                browserSession.getId(), authorizationIdentity(browserSession));
        alarmEventRefresher.requestRefresh(
                identity,
                authentication,
                payload -> sendUserScopedToIdentity(identity, payload));
    }

    private void requestAlarmSnapshot(WebSocketSession browserSession) {
        Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
        String identity = authorizationIdentityBySession.getOrDefault(
                browserSession.getId(), authorizationIdentity(browserSession));
        alarmEventRefresher.requestSnapshot(
                identity,
                authentication,
                payload -> sendUserScopedToIdentity(identity, payload));
    }

    private void sendUserScopedToIdentity(String identity, String payload) {
        sessionsForIdentity(identity).forEach(session -> sendUserScopedToBrowserSession(session, payload));
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
            markAuthorizationUnavailable(identity, snapshot, now);
            if (identity != null && authorizationRetryDue(identity, now)) {
                refreshAuthorizationAsync(identity, newestTokenSession(identity));
            }
            return null;
        }
        return snapshot;
    }

    private AuthorizationSnapshot loadAuthorizationSnapshot(WebSocketSession browserSession) {
        BigscreenWebSocketAuthorizationService.AuthorizedResources resources =
                authorizationService.authorizedResources(browserSession);
        Instant loadedAt = Instant.now();
        return new AuthorizationSnapshot(
                resources,
                loadedAt,
                loadedAt.plusMillis(authorizationTtlMs()));
    }

    private AuthorizationSnapshot initialAuthorizationSnapshot(
            String identity,
            WebSocketSession browserSession) {
        AuthorizationSnapshot cached = authorizationSnapshotsByIdentity.get(identity);
        if (cached != null && !cached.expired(Instant.now())) {
            return cached;
        }
        CompletableFuture<AuthorizationSnapshot> candidate = new CompletableFuture<>();
        CompletableFuture<AuthorizationSnapshot> pending =
                authorizationInitialLoadsByIdentity.putIfAbsent(identity, candidate);
        if (pending == null) {
            pending = candidate;
            try {
                authorizationRefreshExecutor.execute(() -> {
                    try {
                        AuthorizationSnapshot loaded = loadAuthorizationSnapshot(browserSession);
                        if (!storeInitialSnapshotIfReserved(identity, loaded)) {
                            releaseCatalogLease(browserSession);
                        }
                        candidate.complete(loaded);
                    } catch (RuntimeException exception) {
                        candidate.completeExceptionally(exception);
                    } finally {
                        authorizationInitialLoadsByIdentity.remove(identity, candidate);
                    }
                });
            } catch (RuntimeException exception) {
                authorizationInitialLoadsByIdentity.remove(identity, candidate);
                candidate.completeExceptionally(exception);
            }
        }
        try {
            return pending.get(
                    Math.min(MAX_INITIALIZATION_WAIT_MS, Math.max(1000L, initializationWaitMs)),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("大屏 WebSocket 初始化权限加载被中断", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("大屏 WebSocket 初始化权限加载超时", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("大屏 WebSocket 初始化权限加载失败", cause);
        }
    }

    private boolean storeInitialSnapshotIfReserved(String identity, AuthorizationSnapshot snapshot) {
        synchronized (sessionQuotaMonitor) {
            if (!authorizationIdentityBySession.containsValue(identity)) return false;
            authorizationSnapshotsByIdentity.put(identity, snapshot);
            return true;
        }
    }

    private boolean reserveSession(WebSocketSession browserSession, String identity) {
        String organization = authorizationOrganization(browserSession, identity);
        synchronized (sessionQuotaMonitor) {
            String existing = authorizationIdentityBySession.get(browserSession.getId());
            if (existing != null) return existing.equals(identity);
            long identitySessions = authorizationIdentityBySession.values().stream()
                    .filter(identity::equals)
                    .count();
            long organizationSessions = authorizationOrganizationBySession.values().stream()
                    .filter(organization::equals)
                    .count();
            if (authorizationIdentityBySession.size() >= boundedQuota(
                            maxSessionsPerInstance, MAX_INSTANCE_SESSION_QUOTA)
                    || identitySessions >= boundedQuota(maxSessionsPerIdentity, MAX_IDENTITY_SESSION_QUOTA)
                    || organizationSessions >= boundedQuota(
                            maxSessionsPerOrganization, MAX_ORGANIZATION_SESSION_QUOTA)) {
                log.warn("拒绝超过配额的大屏 WebSocket，会话={} 实例会话数={} 身份会话数={} 组织会话数={}",
                        browserSession.getId(), authorizationIdentityBySession.size(),
                        identitySessions, organizationSessions);
                closeForSessionQuota(browserSession);
                return false;
            }
            authorizationIdentityBySession.put(browserSession.getId(), identity);
            authorizationOrganizationBySession.put(browserSession.getId(), organization);
            return true;
        }
    }

    private String releaseSessionReservation(String sessionId) {
        synchronized (sessionQuotaMonitor) {
            authorizationOrganizationBySession.remove(sessionId);
            return authorizationIdentityBySession.remove(sessionId);
        }
    }

    private int boundedQuota(int configured, int hardMaximum) {
        return Math.min(hardMaximum, Math.max(1, configured));
    }

    private String authorizationOrganization(WebSocketSession browserSession, String identity) {
        if (browserSession.getPrincipal() instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String orgId = firstClaim(jwt, "org_id", "orgId", "organization_id", "tenant_id");
            if (orgId != null) {
                String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
                return issuer + "|" + orgId;
            }
        }
        // Token 未声明组织时按身份隔离，不能把无组织声明的不同用户错误归为同一组织。
        return "identity|" + identity;
    }

    private long authorizationTtlMs() {
        return Math.min(MAX_AUTHORIZATION_STALENESS_MS, Math.max(1L, authorizationMaxStalenessMs));
    }

    private long authorizationRefreshAfterMs(String identity, long ttlMillis) {
        long refreshWindowMillis = Math.max(1L, ttlMillis / 5L);
        return Math.max(1L, ttlMillis - (2L * refreshWindowMillis)
                + Math.floorMod(identity.hashCode(), refreshWindowMillis));
    }

    private boolean authorizationRetryDue(String identity, Instant now) {
        AuthorizationRetryState state = authorizationRetryStates.get(identity);
        return state == null || !state.nextAttempt().isAfter(now);
    }

    private void recordAuthorizationRefreshFailure(String identity) {
        authorizationRetryStates.compute(identity, (ignored, previous) -> {
            int failures = previous == null ? 1 : Math.min(5, previous.failures() + 1);
            long delayMillis = Math.min(30000L, 1000L << (failures - 1));
            long jitterMillis = Math.floorMod(identity.hashCode(), 501);
            return new AuthorizationRetryState(failures, Instant.now().plusMillis(delayMillis + jitterMillis));
        });
    }

    private void markAuthorizationUnavailable(
            String identity,
            AuthorizationSnapshot snapshot,
            Instant now) {
        if (identity == null || !authorizationUnavailableIdentities.add(identity)) {
            return;
        }
        log.warn("大屏 WebSocket 授权快照不可用，连接进入 fail-closed，身份={} 快照年龄毫秒={}",
                identity, snapshot == null ? -1 : snapshot.ageMillis(now));
        notifyAuthorizationAvailability(identity, false);
    }

    private String authorizationIdentity(WebSocketSession browserSession) {
        if (browserSession.getPrincipal() instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            String orgId = firstClaim(jwt, "org_id", "orgId", "organization_id", "tenant_id");
            String permissionVersion = firstClaim(
                    jwt, "authorization_version", "permission_version", "auth_version");
            String authorities = jwtAuthentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(","));
            return String.join("|", issuer, jwt.getSubject(), orgId == null ? "" : orgId,
                    permissionVersion == null ? "" : permissionVersion, authorities);
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

    private void removeUnusedRefreshState(String identity) {
        if (identity == null || !sessionsForIdentity(identity).isEmpty()) return;
        statsEventRefresher.remove(identity);
        taskEventRefresher.remove(identity);
        alarmEventRefresher.remove(identity);
    }

    private void removeUnusedIdentity(String identity, WebSocketSession browserSession) {
        synchronized (sessionQuotaMonitor) {
            if (identity == null || authorizationIdentityBySession.containsValue(identity)) return;
            authorizationUnavailableIdentities.remove(identity);
            authorizationRetryStates.remove(identity);
            // remove 返回值同时作为并发关闭时的单次释放闸门。
            if (authorizationSnapshotsByIdentity.remove(identity) == null) return;
        }
        releaseCatalogLease(browserSession);
    }

    private void releaseCatalogLease(WebSocketSession browserSession) {
        HttpHeaders headers = browserSession.getHandshakeHeaders() == null
                ? new HttpHeaders()
                : browserSession.getHandshakeHeaders();
        catalogLeaseClient.release(browserSession.getPrincipal(), headers);
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

    private void notifyAuthorizationAvailability(String identity, boolean available) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "event", "bigscreen.authorization.state",
                    "timestamp", Instant.now().toString(),
                    "data", Map.of("available", available)));
            sessionsForIdentity(identity).forEach(session -> {
                if (!session.isOpen()) return;
                try {
                    sendText(session, payload);
                } catch (Exception exception) {
                    log.debug("发送大屏授权可用状态失败，会话={}", session.getId(), exception);
                }
            });
        } catch (Exception exception) {
            log.warn("序列化大屏授权可用状态失败，身份={}", identity, exception);
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

    private void closeForSessionQuota(WebSocketSession browserSession) {
        try {
            browserSession.close(new CloseStatus(4008, "WebSocket 会话数达到上限"));
        } catch (Exception closeException) {
            log.debug("关闭超过配额的大屏 WebSocket 会话失败，会话={}",
                    browserSession.getId(), closeException);
        }
    }

    @PreDestroy
    void shutdownAuthorizationRefreshExecutor() {
        authorizationRefreshExecutor.shutdownNow();
        centerConnectionExecutor.shutdownNow();
    }

    private void logClose(String side, WebSocketSession session, CloseStatus status) {
        if (isNormalClose(status)) {
            log.debug("{} WebSocket 已关闭，会话={} 状态={}", side, session.getId(), status);
            return;
        }
        log.warn("{} WebSocket 异常关闭，会话={} 状态={}", side, session.getId(), status);
    }

    static boolean isNormalClose(CloseStatus status) {
        return status.getCode() == CloseStatus.NORMAL.getCode()
                || status.getCode() == CloseStatus.GOING_AWAY.getCode();
    }

    private record AuthorizationSnapshot(
            BigscreenWebSocketAuthorizationService.AuthorizedResources resources,
            Instant loadedAt,
            Instant expiresAt) {

        boolean expired(Instant now) {
            return !expiresAt.isAfter(now);
        }

        boolean shouldRefresh(Instant now, long refreshAfterMs) {
            return !loadedAt.plusMillis(refreshAfterMs).isAfter(now);
        }

        long ageMillis(Instant now) {
            return Math.max(0L, Duration.between(loadedAt, now).toMillis());
        }
    }

    private record AuthorizationRetryState(int failures, Instant nextAttempt) {
    }
}
