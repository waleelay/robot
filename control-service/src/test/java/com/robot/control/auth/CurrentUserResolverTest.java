package com.robot.control.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.robot.control.config.ControlServiceProperties;
import com.robot.control.ws.MediaWsAuthHandshakeInterceptor;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketSession;

class CurrentUserResolverTest {

    @Test
    void resolvesTrustedUserForHttpAndWebSocket() {
        CurrentUserResolver resolver = new CurrentUserResolver(new ControlServiceProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "operator-1");
        request.addHeader("X-Org-Id", "org001");
        request.addHeader("X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR");
        request.addHeader("X-Client-Id", "web-tab-1");

        HttpHeaders headers = new HttpHeaders();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.getAttributes()).thenReturn(Map.of(
                MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR,
                Map.of(
                        "X-User-Id", "operator-1",
                        "X-Org-Id", "org001",
                        "X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR")));
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
        CurrentUserResolver resolver = new CurrentUserResolver(new ControlServiceProperties());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
                MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR,
                Map.of(
                        "X-User-Id", "operator-1",
                        "X-Org-Id", "org001",
                        "X-Roles", "EQUIPMENT_OPERATOR")));
        when(session.getUri()).thenReturn(URI.create("wss://center.example/ws/control"));
        when(session.getId()).thenReturn("server-session-id");

        assertThat(resolver.resolve(session).clientId()).isEqualTo("server-session-id");
    }

    @Test
    void resolvesWebSocketUserFromSnapshotWithoutReadingHandshakeHeaders() {
        CurrentUserResolver resolver = new CurrentUserResolver(new ControlServiceProperties());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
                MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR,
                Map.of(
                        "X-User-Id", "operator-1",
                        "X-Org-Id", "org001",
                        "X-Roles", "EQUIPMENT_OPERATOR",
                        "X-Client-Id", "web-tab-1")));

        CurrentUser user = resolver.resolve(session);

        assertThat(user.userId()).isEqualTo("operator-1");
        assertThat(user.orgId()).isEqualTo("org001");
        assertThat(user.clientId()).isEqualTo("web-tab-1");
        assertThat(user.roles()).containsExactly("EQUIPMENT_OPERATOR");
    }

    @Test
    void rejectsMissingTrustedIdentityByDefault() {
        CurrentUserResolver resolver = new CurrentUserResolver(new ControlServiceProperties());
        MockHttpServletRequest request = new MockHttpServletRequest();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-User-Id");
    }

    @Test
    void canAllowDefaultUserForLocalDebugging() {
        ControlServiceProperties properties = new ControlServiceProperties();
        properties.getAuth().setAllowDefaultUser(true);
        CurrentUserResolver resolver = new CurrentUserResolver(properties);

        CurrentUser user = resolver.resolve(new MockHttpServletRequest());

        assertThat(user.userId()).isEqualTo("dev-user");
        assertThat(user.roles()).contains("MEDIA_OPERATOR");
    }
}
