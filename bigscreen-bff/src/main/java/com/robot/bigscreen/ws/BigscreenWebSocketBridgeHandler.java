package com.robot.bigscreen.ws;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
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
    private final AuthenticatedRequestHeaders authenticatedRequestHeaders;
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
    private final Map<String, WebSocketSession> centerSessions = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> browserSessions = ConcurrentHashMap.newKeySet();

    public BigscreenWebSocketBridgeHandler(
            CenterServiceProperties properties,
            PanoramaWebSocketEventAdapter eventAdapter,
            AuthenticatedRequestHeaders authenticatedRequestHeaders) {
        this.properties = properties;
        this.eventAdapter = eventAdapter;
        this.authenticatedRequestHeaders = authenticatedRequestHeaders;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) throws Exception {
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
            log.warn("Center websocket unavailable, browser session will not receive center realtime events session={}",
                    browserSession.getId(), exception);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
        WebSocketSession centerSession = centerSessions.get(browserSession.getId());
        if (centerSession != null && centerSession.isOpen()) {
            centerSession.sendMessage(message);
        } else {
            log.debug("Drop browser websocket message because center websocket is unavailable session={}",
                    browserSession.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) throws Exception {
        browserSessions.remove(browserSession);
        WebSocketSession centerSession = centerSessions.remove(browserSession.getId());
        if (centerSession != null && centerSession.isOpen()) {
            centerSession.close(status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) throws Exception {
        log.warn("Browser websocket transport error session={}", browserSession.getId(), exception);
        afterConnectionClosed(browserSession, CloseStatus.SERVER_ERROR);
    }

    public void broadcastToBrowserSessions(String payload) {
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession browserSession : browserSessions) {
            if (!browserSession.isOpen()) {
                browserSessions.remove(browserSession);
                continue;
            }
            try {
                synchronized (browserSession) {
                    browserSession.sendMessage(message);
                }
            } catch (Exception exception) {
                log.warn("Failed to broadcast browser event session={}", browserSession.getId(), exception);
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
                for (String payload : eventAdapter.adapt(message.getPayload())) {
                    browserSession.sendMessage(new TextMessage(payload));
                }
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession centerSession, CloseStatus status) throws Exception {
            centerSessions.remove(browserSession.getId());
            if (browserSession.isOpen()) {
                browserSession.close(status);
            }
        }

        @Override
        public void handleTransportError(WebSocketSession centerSession, Throwable exception) throws Exception {
            log.warn("Center websocket transport error browserSession={}", browserSession.getId(), exception);
            afterConnectionClosed(centerSession, CloseStatus.SERVER_ERROR);
        }
    }
}
