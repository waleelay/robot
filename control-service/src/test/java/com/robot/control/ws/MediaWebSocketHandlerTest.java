package com.robot.control.ws;

import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
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
}
