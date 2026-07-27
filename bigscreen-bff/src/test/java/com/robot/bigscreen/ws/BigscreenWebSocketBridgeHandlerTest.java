package com.robot.bigscreen.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.robot.bigscreen.config.CenterServiceProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;

class BigscreenWebSocketBridgeHandlerTest {

    @Test
    void forwardsClientIdFromBrowserQuery() {
        BigscreenWebSocketBridgeHandler handler = handler();
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(),
                URI.create("wss://center/ws/control?clientId=web-tab-123"),
                "bff-session");
        WebSocketHttpHeaders forwarded = new WebSocketHttpHeaders();

        handler.copyHandshakeHeaders(browserSession, forwarded);

        assertEquals("web-tab-123", forwarded.getFirst("X-Client-Id"));
    }

    @Test
    void keepsTrustedClientIdHeaderAheadOfQuery() {
        HttpHeaders incoming = new HttpHeaders();
        incoming.set("X-Client-Id", "gateway-client");
        BigscreenWebSocketBridgeHandler handler = handler();
        WebSocketSession browserSession = browserSession(
                incoming,
                URI.create("wss://center/ws/control?clientId=web-tab-123"),
                "bff-session");
        WebSocketHttpHeaders forwarded = new WebSocketHttpHeaders();

        handler.copyHandshakeHeaders(browserSession, forwarded);

        assertEquals("gateway-client", forwarded.getFirst("X-Client-Id"));
    }

    @Test
    void fallsBackToBrowserSessionIdWithoutClientId() {
        BigscreenWebSocketBridgeHandler handler = handler();
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(),
                URI.create("wss://center/ws/control"),
                "bff-session");
        WebSocketHttpHeaders forwarded = new WebSocketHttpHeaders();

        handler.copyHandshakeHeaders(browserSession, forwarded);

        assertEquals("bff-session", forwarded.getFirst("X-Client-Id"));
    }

    private BigscreenWebSocketBridgeHandler handler() {
        return new BigscreenWebSocketBridgeHandler(
                mock(CenterServiceProperties.class),
                mock(PanoramaWebSocketEventAdapter.class));
    }

    private WebSocketSession browserSession(HttpHeaders headers, URI uri, String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.getUri()).thenReturn(uri);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
