package com.robot.bigscreen.ws;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import com.robot.bigscreen.config.WebSocketConfig;
import com.robot.bigscreen.panorama.StatsPart;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
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
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION,
            "X-User-Id",
            "X-Org-Id",
            "X-Roles",
            "X-Client-Id");

    private final CenterServiceProperties properties;
    private final PanoramaWebSocketEventAdapter eventAdapter;
    private final PanoramaLocationEventThrottler locationEventThrottler;
    private final PanoramaStatsEventRefresher statsEventRefresher;
    private final PanoramaTaskEventRefresher taskEventRefresher;
    private final PanoramaAlarmEventRefresher alarmEventRefresher;
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final BigscreenWebSocketAuthorizationService authorizationService;
    private final StandardWebSocketClient webSocketClient;
    private final Map<String, WebSocketSession> centerSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> authorizedRobotIdsBySession = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> browserSessions = ConcurrentHashMap.newKeySet();

    public BigscreenWebSocketBridgeHandler(
            CenterServiceProperties properties,
            PanoramaWebSocketEventAdapter eventAdapter,
            PanoramaLocationEventThrottler locationEventThrottler,
            PanoramaStatsEventRefresher statsEventRefresher,
            PanoramaTaskEventRefresher taskEventRefresher,
            PanoramaAlarmEventRefresher alarmEventRefresher,
            AuthenticatedRequestHeaders authenticatedRequestHeaders,
            BigscreenWebSocketAuthorizationService authorizationService) {
        this.properties = properties;
        this.eventAdapter = eventAdapter;
        this.locationEventThrottler = locationEventThrottler;
        this.statsEventRefresher = statsEventRefresher;
        this.taskEventRefresher = taskEventRefresher;
        this.alarmEventRefresher = alarmEventRefresher;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
        this.authorizationService = authorizationService;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        container.setDefaultMaxBinaryMessageBufferSize(WebSocketConfig.MAX_TEXT_MESSAGE_SIZE);
        this.webSocketClient = new StandardWebSocketClient(container);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
        Set<String> authorizedRobotIds = authorizationService.authorizedRobotIds(browserSession);
        authorizedRobotIdsBySession.put(browserSession.getId(), authorizedRobotIds);
        log.debug("大屏 WebSocket 会话授权设备加载完成，会话={} 数量={}",
                browserSession.getId(), authorizedRobotIds.size());
        browserSessions.add(browserSession);
        try {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            copyHandshakeHeaders(browserSession, headers);
            WebSocketHandler centerHandler = new CenterToBrowserHandler(browserSession);
            WebSocketSession centerSession = webSocketClient
                    .execute(centerHandler, headers, centerUri(browserSession))
                    .get();
            centerSessions.put(browserSession.getId(), centerSession);
        } catch (Exception exception) {
            log.warn("中心端 WebSocket 不可用，浏览器会话将无法接收中心端实时事件，会话={}",
                    browserSession.getId(), exception);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
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
        authorizedRobotIdsBySession.remove(browserSession.getId());
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
                            payload -> sendToBrowserSession(browserSession, payload),
                            statsParts);
                }
                if (refreshTasks) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    taskEventRefresher.requestRefresh(
                            browserSession.getId(),
                            authentication,
                            payload -> sendToBrowserSession(browserSession, payload));
                }
                if (refreshAlarms) {
                    Authentication authentication = browserSession.getPrincipal() instanceof Authentication value ? value : null;
                    alarmEventRefresher.requestRefresh(
                            browserSession.getId(),
                            authentication,
                            payload -> sendToBrowserSession(browserSession, payload));
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
        Set<String> authorizedRobotIds = authorizedRobotIdsBySession.get(browserSession.getId());
        if (!authorizationService.canReceive(authorizedRobotIds, payload)) {
            log.debug("已过滤无权限设备 WebSocket 事件，会话={}", browserSession.getId());
            return;
        }
        try {
            synchronized (browserSession) {
                browserSession.sendMessage(new TextMessage(payload));
            }
        } catch (Exception exception) {
            log.warn("向浏览器发送事件失败，会话={}", browserSession.getId(), exception);
        }
    }

    private void logClose(String side, WebSocketSession session, CloseStatus status) {
        if (CloseStatus.NORMAL.equals(status) || CloseStatus.GOING_AWAY.equals(status)) {
            log.debug("{} WebSocket 已关闭，会话={} 状态={}", side, session.getId(), status);
            return;
        }
        log.warn("{} WebSocket 异常关闭，会话={} 状态={}", side, session.getId(), status);
    }
}
