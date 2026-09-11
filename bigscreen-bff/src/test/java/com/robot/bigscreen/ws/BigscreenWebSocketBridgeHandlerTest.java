package com.robot.bigscreen.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
    void usesBoundedExecutorsForAuthorizationRefreshAndCenterConnections() {
        BigscreenWebSocketBridgeHandler handler = handler();

        ThreadPoolExecutor authorizationExecutor = (ThreadPoolExecutor) ReflectionTestUtils.getField(
                handler, "authorizationRefreshExecutor");
        ThreadPoolExecutor centerExecutor = (ThreadPoolExecutor) ReflectionTestUtils.getField(
                handler, "centerConnectionExecutor");

        assertEquals(16, authorizationExecutor.getMaximumPoolSize());
        assertEquals(64, authorizationExecutor.getQueue().remainingCapacity());
        assertEquals(16, centerExecutor.getMaximumPoolSize());
        assertEquals(64, centerExecutor.getQueue().remainingCapacity());
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void spreadsSoftRefreshAcrossTheThirdAndFourthFifthsOfAuthorizationTtl() {
        BigscreenWebSocketBridgeHandler handler = handler();

        long refreshAfterMs = (Long) ReflectionTestUtils.invokeMethod(
                handler, "authorizationRefreshAfterMs", "identity-a", 300000L);

        assertTrue(refreshAfterMs >= 180000L);
        assertTrue(refreshAfterMs < 240000L);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void redactsCredentialsFromWebSocketConnectionFailureReason() {
        String reason = BigscreenWebSocketBridgeHandler.sanitizedConnectionFailureReason(
                new IllegalStateException("Handshake failed for ws://control/ws?clientId=tab-1&access_token=secret.jwt "
                        + "Authorization: Bearer another-secret"));

        assertFalse(reason.contains("secret.jwt"));
        assertFalse(reason.contains("another-secret"));
        assertTrue(reason.contains("access_token=[已脱敏]"));
        assertTrue(reason.contains("Bearer [已脱敏]"));
    }

    @Test
    void classifiesNormalCloseByCodeRegardlessOfReason() {
        assertTrue(BigscreenWebSocketBridgeHandler.isNormalClose(
                new CloseStatus(1000, "load phase complete")));
        assertTrue(BigscreenWebSocketBridgeHandler.isNormalClose(
                new CloseStatus(1001, "browser navigation")));
        assertFalse(BigscreenWebSocketBridgeHandler.isNormalClose(CloseStatus.SERVER_ERROR));
    }

    @Test
    void refreshesTasksAfterConnectingAndClosesBrowserOnInitialUpstreamFailure() throws Exception {
        BigscreenWebSocketAuthorizationService authorization = mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browser = browserSession(new HttpHeaders(), URI.create("ws://bigscreen/ws/bigscreen"), "task-test");
        when(authorization.authorizedResources(browser)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = org.mockito.Mockito.spy(handler(authorization));
        PanoramaTaskEventRefresher refresher = (PanoramaTaskEventRefresher) ReflectionTestUtils.getField(handler, "taskEventRefresher");
        handler.afterConnectionEstablished(browser);
        verify(refresher).requestRefresh(eq("session|task-test"), any(), any(), eq(false));
        org.mockito.Mockito.doThrow(new IllegalStateException("upstream unavailable")).when(handler).connectCenter(browser);
        handler.afterConnectionEstablished(browser);
        verify(browser).close(CloseStatus.SERVER_ERROR);
        verify(refresher, times(1)).requestRefresh(eq("session|task-test"), any(), any(), eq(false));
        handler.shutdownAuthorizationRefreshExecutor();
    }

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
    void sharesConcurrentInitialAuthorizationLoadForSameIdentity() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        JwtAuthenticationToken authentication = authentication("user-001", Instant.now().plusSeconds(300));
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(authentication);
        when(second.getPrincipal()).thenReturn(authentication);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(authorizationService.authorizedResources(first)).thenAnswer(ignored -> {
            started.countDown();
            assertTrue(release.await(1, TimeUnit.SECONDS));
            return new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of());
        });
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        CompletableFuture<Void> firstConnect = CompletableFuture.runAsync(() -> establish(handler, first));
        assertTrue(started.await(1, TimeUnit.SECONDS));
        CompletableFuture<Void> secondConnect = CompletableFuture.runAsync(() -> establish(handler, second));
        release.countDown();
        CompletableFuture.allOf(firstConnect, secondConnect).get(2, TimeUnit.SECONDS);

        verify(authorizationService).authorizedResources(first);
        verify(authorizationService, never()).authorizedResources(second);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void rejectsAndReleasesPerIdentitySessionQuota() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        JwtAuthenticationToken authentication = authentication("user-001", Instant.now().plusSeconds(300));
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession rejected = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-rejected");
        WebSocketSession replacement = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-replacement");
        when(first.getPrincipal()).thenReturn(authentication);
        when(rejected.getPrincipal()).thenReturn(authentication);
        when(replacement.getPrincipal()).thenReturn(authentication);
        when(authorizationService.authorizedResources(any())).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "maxSessionsPerIdentity", 1);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(rejected);
        verify(rejected).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4008));
        verify(authorizationService, never()).authorizedResources(rejected);

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        handler.afterConnectionEstablished(replacement);
        verify(replacement, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4008));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void rejectsOrganizationSessionQuotaAcrossDifferentUsers() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(
                authentication("user-001", Instant.now().plusSeconds(300), "org-001"));
        when(second.getPrincipal()).thenReturn(
                authentication("user-002", Instant.now().plusSeconds(300), "org-001"));
        when(authorizationService.authorizedResources(first)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "maxSessionsPerOrganization", 1);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(second).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4008));
        verify(authorizationService, never()).authorizedResources(second);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void rejectsInstanceSessionQuotaAcrossDifferentUsers() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(second.getPrincipal()).thenReturn(authentication("user-002", Instant.now().plusSeconds(300)));
        when(authorizationService.authorizedResources(first)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "maxSessionsPerInstance", 1);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(second).close(org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4008));
        verify(authorizationService, never()).authorizedResources(second);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void requestsAlarmSnapshotWhenBrowserConnects() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        PanoramaAlarmEventRefresher alarmEventRefresher = mock(PanoramaAlarmEventRefresher.class);
        JwtAuthenticationToken authentication = authentication("user-001", Instant.now().plusSeconds(300));
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-alarm-snapshot");
        when(browserSession.getPrincipal()).thenReturn(authentication);
        when(authorizationService.authorizedResources(browserSession)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(
                authorizationService, mock(FixedCameraCatalogLeaseClient.class), alarmEventRefresher);

        handler.afterConnectionEstablished(browserSession);

        verify(alarmEventRefresher).requestSnapshot(
                eq("https://iam.example/realms/platform|user-001|||"), eq(authentication), any());
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void sharesRefreshStateAndBroadcastsSnapshotAcrossSessionsOfSameIdentity() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        FixedCameraCatalogLeaseClient catalogLeaseClient = mock(FixedCameraCatalogLeaseClient.class);
        PanoramaStatsEventRefresher statsEventRefresher = mock(PanoramaStatsEventRefresher.class);
        PanoramaTaskEventRefresher taskEventRefresher = mock(PanoramaTaskEventRefresher.class);
        PanoramaAlarmEventRefresher alarmEventRefresher = mock(PanoramaAlarmEventRefresher.class);
        JwtAuthenticationToken authentication = authentication("user-001", Instant.now().plusSeconds(300));
        String identity = "https://iam.example/realms/platform|user-001|||";
        WebSocketSession first = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-first");
        WebSocketSession second = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-second");
        when(first.getPrincipal()).thenReturn(authentication);
        when(second.getPrincipal()).thenReturn(authentication);
        when(first.isOpen()).thenReturn(true);
        when(second.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(first)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(
                authorizationService,
                catalogLeaseClient,
                statsEventRefresher,
                taskEventRefresher,
                alarmEventRefresher);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(taskEventRefresher, times(2)).requestRefresh(eq(identity), any(), any(), eq(false));
        ArgumentCaptor<Consumer<String>> publishers = ArgumentCaptor.forClass(Consumer.class);
        verify(alarmEventRefresher, times(2)).requestSnapshot(eq(identity), any(), publishers.capture());
        publishers.getAllValues().get(0).accept("{\"event\":\"panorama.workflow-alarms.changed\"}");
        verify(first).sendMessage(any(TextMessage.class));
        verify(second).sendMessage(any(TextMessage.class));

        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        verify(statsEventRefresher, never()).remove(identity);
        verify(taskEventRefresher, never()).remove(identity);
        verify(alarmEventRefresher, never()).remove(identity);

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);
        verify(statsEventRefresher).remove(identity);
        verify(taskEventRefresher).remove(identity);
        verify(alarmEventRefresher).remove(identity);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void keepsConnectionFailClosedWithoutSendingEventWithExpiredAuthorizationSnapshot() throws Exception {
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

        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(browserSession).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("bigscreen.authorization.state"));
        assertTrue(messages.getValue().getPayload().contains("\"available\":false"));
        assertFalse(messages.getValue().getPayload().contains("robot.state"));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void startsAuthorizationTtlAfterSlowRemoteQueryCompletes() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-slow-authorization");
        when(browserSession.getPrincipal()).thenReturn(authentication("user-001", Instant.now().plusSeconds(300)));
        when(browserSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession)).thenAnswer(ignored -> {
            Thread.sleep(200L);
            return new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of("robot-001"), Set.of());
        });
        when(authorizationService.canReceive(
                any(BigscreenWebSocketAuthorizationService.AuthorizedResources.class), anyString()))
                .thenReturn(true);
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 150L);

        handler.afterConnectionEstablished(browserSession);
        handler.broadcastToBrowserSessions(
                "{\"event\":\"robot.state\",\"data\":{\"robotId\":\"robot-001\"}}");

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(browserSession).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("robot.state"));
        assertFalse(messages.getValue().getPayload().contains("bigscreen.authorization.state"));
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void rejectsLateCenterRegistrationAndClosesRegisteredCenterDuringCleanup() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession browserSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/control"), "session-center-race");
        WebSocketSession centerSession = mock(WebSocketSession.class);
        WebSocketSession lateCenterSession = mock(WebSocketSession.class);
        when(browserSession.isOpen()).thenReturn(true);
        when(centerSession.isOpen()).thenReturn(true);
        when(authorizationService.authorizedResources(browserSession)).thenReturn(
                new BigscreenWebSocketAuthorizationService.AuthorizedResources(Set.of(), Set.of()));
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);
        handler.afterConnectionEstablished(browserSession);

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(
                handler, "registerCenterSession", browserSession, centerSession));
        handler.afterConnectionClosed(browserSession, CloseStatus.NORMAL);
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(
                handler, "registerCenterSession", browserSession, lateCenterSession));

        verify(centerSession).close(CloseStatus.NORMAL);
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
    void forwardsFieldCallMessageWithoutLoadingBigscreenAuthorization() throws Exception {
        BigscreenWebSocketAuthorizationService authorizationService =
                mock(BigscreenWebSocketAuthorizationService.class);
        WebSocketSession appSession = browserSession(
                new HttpHeaders(), URI.create("wss://bigscreen/ws/field-call"), "field-call-session");
        WebSocketSession centerSession = mock(WebSocketSession.class);
        when(appSession.getPrincipal()).thenReturn(authentication("field-user", Instant.now().plusSeconds(300)));
        when(appSession.isOpen()).thenReturn(true);
        when(centerSession.isOpen()).thenReturn(true);
        BigscreenWebSocketBridgeHandler handler = handler(authorizationService);

        handler.afterConnectionEstablished(appSession);
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(
                handler, "registerCenterSession", appSession, centerSession));
        TextMessage invite = new TextMessage("{\"type\":\"field.call.invite\"}");
        handler.handleTextMessage(appSession, invite);

        verify(authorizationService, never()).authorizedResources(appSession);
        verify(authorizationService, never()).canForwardClientMessage(any(), anyString());
        verify(centerSession).sendMessage(invite);
        handler.shutdownAuthorizationRefreshExecutor();
    }

    @Test
    void keepsConnectionFailClosedWhenAsynchronousRefreshFailsAndSnapshotExpires() throws Exception {
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
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 300L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(260L);

        handler.refreshSessionAuthorizations();

        verify(authorizationService, timeout(1000).times(2)).authorizedResources(browserSession);
        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        Thread.sleep(60L);
        handler.refreshSessionAuthorizations();
        verify(browserSession, never()).close(
                org.mockito.ArgumentMatchers.argThat(status -> status.getCode() == 4003));
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(browserSession, timeout(1000)).sendMessage(messages.capture());
        assertTrue(messages.getValue().getPayload().contains("bigscreen.authorization.state"));
        assertTrue(messages.getValue().getPayload().contains("\"available\":false"));
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
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 300L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(260L);

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
        ReflectionTestUtils.setField(handler, "authorizationMaxStalenessMs", 200L);
        handler.afterConnectionEstablished(browserSession);
        Thread.sleep(175L);
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
        return handler(authorizationService, catalogLeaseClient, mock(PanoramaAlarmEventRefresher.class));
    }

    private BigscreenWebSocketBridgeHandler handler(
            BigscreenWebSocketAuthorizationService authorizationService,
            FixedCameraCatalogLeaseClient catalogLeaseClient,
            PanoramaAlarmEventRefresher alarmEventRefresher) {
        return handler(
                authorizationService,
                catalogLeaseClient,
                mock(PanoramaStatsEventRefresher.class),
                mock(PanoramaTaskEventRefresher.class),
                alarmEventRefresher);
    }

    private BigscreenWebSocketBridgeHandler handler(
            BigscreenWebSocketAuthorizationService authorizationService,
            FixedCameraCatalogLeaseClient catalogLeaseClient,
            PanoramaStatsEventRefresher statsEventRefresher,
            PanoramaTaskEventRefresher taskEventRefresher,
            PanoramaAlarmEventRefresher alarmEventRefresher) {
        return new BigscreenWebSocketBridgeHandler(
                mock(CenterServiceProperties.class),
                mock(PanoramaWebSocketEventAdapter.class),
                mock(PanoramaLocationEventThrottler.class),
                statsEventRefresher,
                taskEventRefresher,
                alarmEventRefresher,
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
        return authentication(subject, expiresAt, null);
    }

    private JwtAuthenticationToken authentication(String subject, Instant expiresAt, String orgId) {
        Jwt.Builder builder = Jwt.withTokenValue("token-" + subject)
                .header("alg", "RS256")
                .issuer("https://iam.example/realms/platform")
                .subject(subject)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(expiresAt);
        if (orgId != null) builder.claim("org_id", orgId);
        Jwt jwt = builder.build();
        return new JwtAuthenticationToken(jwt);
    }

    private void establish(BigscreenWebSocketBridgeHandler handler, WebSocketSession session) {
        try {
            handler.afterConnectionEstablished(session);
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }
}
