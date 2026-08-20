package com.robot.control.ws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUserResolver;
import com.robot.control.auth.RequestAuthorizationHeaders;
import com.robot.control.call.IntercomCallService;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.service.EquipmentControlService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketSession;

class MediaWebSocketHandlerTest {

    @Test
    void warmsAuthorizedDeviceCacheWhenWebSocketConnects() {
        MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
        RequestAuthorizationHeaders authorizationHeaders = mock(RequestAuthorizationHeaders.class);
        ControlManagementClient managementClient = mock(ControlManagementClient.class);
        MediaWebSocketHandler handler = new MediaWebSocketHandler(
                publisher,
                new ObjectMapper(),
                mock(EquipmentControlService.class),
                mock(IntercomCallService.class),
                mock(CurrentUserResolver.class),
                authorizationHeaders,
                managementClient);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, String> headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        when(session.getId()).thenReturn("ws-001");
        when(session.getAttributes()).thenReturn(Map.of(MediaWsAuthHandshakeInterceptor.HTTP_HEADERS_ATTR, headers));

        handler.afterConnectionEstablished(session);

        verify(publisher).addSession(session);
        verify(authorizationHeaders).setWebSocketHeaders(headers);
        verify(managementClient).warmCurrentUserDeviceCache();
        verify(authorizationHeaders).clearWebSocketHeaders();
    }
}
