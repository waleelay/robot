package com.robot.bigscreen.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.bigscreen.auth.AuthenticatedRequestHeaders;
import com.robot.bigscreen.config.CenterServiceProperties;
import com.robot.bigscreen.fixedcamera.FixedCameraCatalogLeaseClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.mockito.ArgumentCaptor;

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

    @Test
    void forwardsWebsocketAccessTokenToCenter() {
        CenterServiceProperties properties = new CenterServiceProperties();
        properties.setWebsocketControlUrl("ws://control-service:8082/ws/control");
        BigscreenWebSocketBridgeHandler handler = new BigscreenWebSocketBridgeHandler(
                properties,
                mock(PanoramaWebSocketEventAdapter.class),
                mock(PanoramaLocationEventThrottler.class),
                mock(PanoramaStatsEventRefresher.class),
                mock(PanoramaTaskEventRefresher.class),
                mock(PanoramaAlarmEventRefresher.class),
                mock(AuthenticatedRequestHeaders.class),
                mock(BigscreenWebSocketAuthorizationService.class),
                mock(FixedCameraCatalogLeaseClient.class),
                new ObjectMapper());
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(),
                URI.create("wss://bigscreen/ws/control?clientId=web-tab-123&access_token=jwt-token"),
                "bff-session");
        WebSocketHttpHeaders forwarded = new WebSocketHttpHeaders();

        handler.copyHandshakeHeaders(browserSession, forwarded);

        assertEquals("Bearer jwt-token", forwarded.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(
                "ws://control-service:8082/ws/control?clientId=web-tab-123&access_token=jwt-token",
                handler.centerUri(browserSession).toString());
    }

    @Test
    void closesWith4003WhenInitialAuthorizationCannotBeLoaded() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-4003");
        when(authorizationService.authorizedResources(browserSession))
                .thenThrow(new IllegalStateException("Management 不可用"));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(browserSession);

        verify(browserSession).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void closesWith4001WithoutLoadingAuthorizationWhenTokenExpired() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-4001");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().minusSeconds(1)));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(browserSession);

        verify(browserSession).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4001));
        verify(authorizationService, never()).authorizedResources(browserSession);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void closesWith4001WhenInitialAuthorizationRejectsCredential() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-rejected-credential");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(authorizationService.authorizedResources(browserSession))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(browserSession);

        verify(browserSession).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4001));
        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void reusesValidAuthorizationSnapshotForSameIdentity() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        JwtAuthenticationToken authentication = authentication("user-001", Instant.now().plusSeconds(300));
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(authentication);
        when(second.getPrincipal()).thenReturn(authentication);
        when(authorizationService.authorizedResources(first)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of("robot-001"), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(authorizationService, times(1)).authorizedResources(first);
        verify(authorizationService, never()).authorizedResources(second);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void closesSessionBeforeSendingEventWithExpiredAuthorizationSnapshot() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-expired-snapshot");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of("robot-001"), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 1L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(5L);

        handler.broadcastToBrowserSessions("{\"event\":\"robot.state\",\"data\":{\"robotId\":\"robot-001\"}}");

        verify(browserSession).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        verify(browserSession, never()).sendMessage(org.mockito.ArgumentMatchers.any());
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void rejectsUnauthorizedClientMessageWithoutForwardingToControl() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-forbidden-command");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of("robot-001"), Set.of()));
        when(authorizationService.canForwardClientMessage(any(), anyString())).thenReturn(false);
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(browserSession);
        handler.handleTextMessage(browserSession, new TextMessage("""
                {"type":"control.command","requestId":"request-001","payload":{"robotId":"robot-002"}}
                """));

        ArgumentCaptor<TextMessage> response = ArgumentCaptor.forClass(TextMessage.class);
        verify(browserSession).sendMessage(response.capture());
        assertTrue(response.getValue().getPayload().contains("RESOURCE_FORBIDDEN"));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void keepsValidSnapshotWhenAsynchronousRefreshFailsAndClosesAfterExpiry() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-refresh-failure");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession))
                .thenReturn(new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("robot-001"), Set.of()))
                .thenThrow(new IllegalStateException("Management 刷新失败"));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 200L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(110L);

        handler.refreshSessionAuthorizations();

        verify(authorizationService, timeout(1000).times(2)).authorizedResources(browserSession);
        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        Thread.sleep(110L);
        handler.refreshSessionAuthorizations();
        verify(browserSession, timeout(1000)).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void closesWith4001WhenAsynchronousRefreshRejectsCredential() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-refresh-unauthorized");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession))
                .thenReturn(new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("robot-001"), Set.of()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 200L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(110L);

        handler.refreshSessionAuthorizations();

        verify(authorizationService, timeout(1000).times(2)).authorizedResources(browserSession);
        verify(browserSession, timeout(1000)).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4001));
        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void stopsSendingRevokedResourceAfterSuccessfulRefresh() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-revoked-resource");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession))
                .thenReturn(new BigscreenWebSocketAuthorizationService.AuthorizedResources(
                        Set.of("robot-001"), Set.of()))
                .thenReturn(new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        when(authorizationService.canReceive(
                any(BigscreenWebSocketAuthorizationService.AuthorizedResources.class),
                anyString())).thenAnswer(invocation -> {
            BigscreenWebSocketAuthorizationService.AuthorizedResources resources = invocation.getArgument(0);
            return resources.robotIds().contains("robot-001");
        });
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 40L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(22L);
        handler.refreshSessionAuthorizations();
        verify(authorizationService, timeout(1000).times(2)).authorizedResources(browserSession);
        Thread.sleep(10L);

        handler.broadcastToBrowserSessions(
                "{\"event\":\"robot.state\",\"data\":{\"robotId\":\"robot-001\"}}");

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(browserSession, times(1)).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("bigscreen.authorization.changed"));
        assertTrue(!messages.getValue().getPayload().contains("robot.state"));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void releasesCatalogLeaseWhenLastIdentitySessionCloses() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService = mock(BigscreenWebSocketAuthorizationService.class);
        FixedCameraCatalogLeaseClient catalogLeaseClient = mock(FixedCameraCatalogLeaseClient.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-last");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(authorizationService.authorizedResources(browserSession)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of("camera-001")));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService, catalogLeaseClient);

        handler.afterConnectionEstablished(browserSession);
        handler.afterConnectionClosed(browserSession, CloseStatus.NORMAL);

        verify(catalogLeaseClient).release(browserSession.getPrincipal(), browserSession.getHandshakeHeaders());
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void keepsCatalogLeaseWhileSameIdentityHasAnotherSession() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService = mock(BigscreenWebSocketAuthorizationService.class);
        FixedCameraCatalogLeaseClient catalogLeaseClient = mock(FixedCameraCatalogLeaseClient.class);
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(second.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(authorizationService.authorizedResources(any())).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of("camera-001")));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService, catalogLeaseClient);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        verify(catalogLeaseClient, never()).release(any(), any());

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);
        verify(catalogLeaseClient).release(second.getPrincipal(), second.getHandshakeHeaders());
        handler.shutdownAuthorizationRefreshExecutor();
    }

    private BigscreenWebSocketBridgeHandler handler() {
        return handler(mock(BigscreenWebSocketAuthorizationService.class));
    }

    private BigscreenWebSocketBridgeHandler handler(
            BigscreenWebSocketAuthorizationService authorizationService) {
        return handler(authorizationService, mock(FixedCameraCatalogLeaseClient.class));
    }

    private BigscreenWebSocketBridgeHandler handler(
            BigscreenWebSocketAuthorizationService authorizationService,
            FixedCameraCatalogLeaseClient catalogLeaseClient) {
        return new BigscreenWebSocketBridgeHandler(
                mock(CenterServiceProperties.class),
                mock(PanoramaWebSocketEventAdapter.class),
                mock(PanoramaLocationEventThrottler.class),
                mock(PanoramaStatsEventRefresher.class),
                mock(PanoramaTaskEventRefresher.class),
                mock(PanoramaAlarmEventRefresher.class),
                mock(AuthenticatedRequestHeaders.class),
                authorizationService,
                catalogLeaseClient,
                new ObjectMapper()) {
            @Override
            void connectCenter(WebSocketSession browserSession) {
                // 单元测试不连接真实 Control WebSocket。
            }
        };
    }

    private WebSocketSession browserSession(HttpHeaders headers, URI uri, String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.getUri()).thenReturn(uri);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private JwtAuthenticationToken authentication(String subject, Instant expiresAt) {
        Jwt jwt = Jwt.withTokenValue("token-" + subject)
                .header("alg", "RS256")
                .issuer("https://iam.example/realms/platform")
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
