package com.robot.control.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.call.FieldCallService;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.service.EquipmentControlService;
import com.robot.control.trajectory.TrajectoryCoordinator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class MediaWebSocketHandlerTest {

    @Test
    void warmsAuthorizedDeviceCacheWhenWebSocketConnects() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RequestAuthorizationHeaders authorizationHeaders = mock(RequestAuthorizationHeaders.class);
        ControlManagementClient managementClient = mock(ControlManagementClient.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                mock(EquipmentControlService.class),
                mock(IntercomCallService.class),
                mock(FieldCallService.class),
                currentUserResolver,
                authorizationHeaders,
                managementClient,
                mock(TrajectoryCoordinator.class));
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, String> headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        when(session.getId()).thenReturn("ws-001");
        when(session.getAttributes()).thenReturn(Map.of(MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR, headers));
        when(currentUserResolver.resolve(session)).thenReturn(
                new CurrentUser("user-1", "org-1", Set.of("EQUIPMENT_OPERATOR"), "client-1"));

        handler.afterConnectionEstablished(session);

        verify(publisher).addSession(session);
        verify(authorizationHeaders).setWebSocketHeaders(headers);
        verify(managementClient).warmCurrentUserDeviceCache();
        verify(authorizationHeaders).clearWebSocketHeaders();
    }

    @Test
    void releasesTerminalControlSessionsWhenLastWebSocketCloses() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RequestAuthorizationHeaders authorizationHeaders = mock(RequestAuthorizationHeaders.class);
        EquipmentControlService equipmentControlService = mock(EquipmentControlService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        TrajectoryCoordinator trajectoryCoordinator = mock(TrajectoryCoordinator.class);
        CurrentUser user = new CurrentUser("user-1", "org-1", Set.of("EQUIPMENT_OPERATOR"), "client-1");
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                equipmentControlService,
                mock(IntercomCallService.class),
                mock(FieldCallService.class),
                currentUserResolver,
                authorizationHeaders,
                mock(ControlManagementClient.class),
                trajectoryCoordinator);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, String> headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        when(session.getId()).thenReturn("ws-001");
        when(session.getAttributes()).thenReturn(Map.of(MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR, headers));
        when(currentUserResolver.resolve(session)).thenReturn(user);
        when(equipmentControlService.releaseOwnedSessions(user, "websocket_disconnected")).thenReturn(1);

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(authorizationHeaders, times(2)).setWebSocketHeaders(headers);
        verify(equipmentControlService).releaseOwnedSessions(user, "websocket_disconnected");
        verify(trajectoryCoordinator).removeSession(session);
        verify(publisher).removeSession(session);
    }

    @Test
    void keepsControlSessionsWhileSameTerminalStillHasAnotherWebSocket() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RequestAuthorizationHeaders authorizationHeaders = mock(RequestAuthorizationHeaders.class);
        EquipmentControlService equipmentControlService = mock(EquipmentControlService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        CurrentUser user = new CurrentUser("user-1", "org-1", Set.of("EQUIPMENT_OPERATOR"), "client-1");
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                equipmentControlService,
                mock(IntercomCallService.class),
                mock(FieldCallService.class),
                currentUserResolver,
                authorizationHeaders,
                mock(ControlManagementClient.class),
                mock(TrajectoryCoordinator.class));
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);
        when(first.getId()).thenReturn("ws-001");
        when(second.getId()).thenReturn("ws-002");
        when(first.getAttributes()).thenReturn(Map.of());
        when(second.getAttributes()).thenReturn(Map.of());
        when(currentUserResolver.resolve(first)).thenReturn(user);
        when(currentUserResolver.resolve(second)).thenReturn(user);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        verify(equipmentControlService, never()).releaseOwnedSessions(user, "websocket_disconnected");

        handler.afterConnectionClosed(second, CloseStatus.NORMAL);
        verify(equipmentControlService).releaseOwnedSessions(user, "websocket_disconnected");
    }

    @Test
    void doesNotReleaseControlWhileReplacementWebSocketIsBeingRegistered() throws Exception {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        EquipmentControlService equipmentControlService = mock(EquipmentControlService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        CurrentUser user = new CurrentUser("user-1", "org-1", Set.of("EQUIPMENT_OPERATOR"), "client-1");
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                equipmentControlService,
                mock(IntercomCallService.class),
                mock(FieldCallService.class),
                currentUserResolver,
                mock(RequestAuthorizationHeaders.class),
                mock(ControlManagementClient.class),
                mock(TrajectoryCoordinator.class));
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession replacement = mock(WebSocketSession.class);
        when(first.getId()).thenReturn("ws-001");
        when(replacement.getId()).thenReturn("ws-002");
        when(first.getAttributes()).thenReturn(Map.of());
        when(replacement.getAttributes()).thenReturn(Map.of());
        when(currentUserResolver.resolve(first)).thenReturn(user);
        when(currentUserResolver.resolve(replacement)).thenReturn(user);
        handler.afterConnectionEstablished(first);

        CountDownLatch replacementRegistrationStarted = new CountDownLatch(1);
        CountDownLatch allowReplacementRegistration = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == replacement) {
                replacementRegistrationStarted.countDown();
                assertThat(allowReplacementRegistration.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(publisher).addSession(replacement);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> connect = executor.submit(() -> handler.afterConnectionEstablished(replacement));
            assertThat(replacementRegistrationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<?> close = executor.submit(() -> handler.afterConnectionClosed(first, CloseStatus.NORMAL));

            assertThatThrownBy(() -> close.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowReplacementRegistration.countDown();
            connect.get(1, TimeUnit.SECONDS);
            close.get(1, TimeUnit.SECONDS);
            verify(equipmentControlService, never())
                    .releaseOwnedSessions(user, "websocket_disconnected");
        } finally {
            allowReplacementRegistration.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsUnsupportedMessageTypeWithOriginalRequestId() throws Exception {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        CurrentUser user = new CurrentUser("user-1", "org-1", Set.of("EQUIPMENT_OPERATOR"), "client-1");
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                mock(EquipmentControlService.class),
                mock(IntercomCallService.class),
                mock(FieldCallService.class),
                currentUserResolver,
                mock(RequestAuthorizationHeaders.class),
                mock(ControlManagementClient.class),
                mock(TrajectoryCoordinator.class));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("ws-unsupported");
        when(session.getAttributes()).thenReturn(Map.of());
        when(currentUserResolver.resolve(session)).thenReturn(user);
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"unknown.command","requestId":"request-unsupported-001","payload":{}}
                """));

        ArgumentCaptor<TextMessage> response = ArgumentCaptor.forClass(TextMessage.class);
        verify(publisher).send(eq(session), response.capture());
        org.assertj.core.api.Assertions.assertThat(response.getValue().getPayload())
                .contains("request-unsupported-001")
                .contains("UNSUPPORTED_MESSAGE_TYPE");
    }
}
