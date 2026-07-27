package com.robot.control.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.robot.control.config.ControlServiceProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketSession;

class CurrentUserResolverTest {

    private final CurrentUserResolver resolver = new CurrentUserResolver(new ControlServiceProperties());

    @Test
    void resolvesSameDefaultUserForHttpAndWebSocket() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Client-Id", "web-tab-1");

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.getUri()).thenReturn(URI.create("wss://center.example/ws/control?clientId=web-tab-1"));
        when(session.getId()).thenReturn("server-session-id");

        CurrentUser httpUser = resolver.resolve(request);
        CurrentUser webSocketUser = resolver.resolve(session);

        assertThat(webSocketUser.userId()).isEqualTo(httpUser.userId());
        assertThat(webSocketUser.orgId()).isEqualTo(httpUser.orgId());
        assertThat(webSocketUser.clientId()).isEqualTo(httpUser.clientId());
        assertThat(webSocketUser.roles()).contains("MEDIA_OPERATOR");
    }

    @Test
    void fallsBackToWebSocketSessionIdWithoutClientQuery() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.getUri()).thenReturn(URI.create("wss://center.example/ws/control"));
        when(session.getId()).thenReturn("server-session-id");

        assertThat(resolver.resolve(session).clientId()).isEqualTo("server-session-id");
    }
}
