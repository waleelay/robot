package com.robot.control.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class FieldCallServiceTest {

    private final ControlMediaServiceClient mediaServiceClient = mock(ControlMediaServiceClient.class);
    private final MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
    private final FieldCallService service = new FieldCallService(
            mediaServiceClient,
            publisher,
            new ObjectMapper());

    @Test
    void rejectsCallerWithoutFieldOperatorRole() {
        CurrentUser user = new CurrentUser("app-user", "org-1", Set.of("AUTHENTICATED"), "app-1");

        assertThatThrownBy(() -> service.invite(user, "现场人员", mock(WebSocketSession.class)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("当前用户没有现场呼叫发起权限");
    }

    @Test
    void acceptsCallerWithFieldOperatorRole() {
        CurrentUser user = new CurrentUser("app-user", "org-1", Set.of("FIELD_OPERATOR"), "app-1");

        Map<String, Object> result = service.invite(user, "现场人员", mock(WebSocketSession.class));

        assertThat(result).containsEntry("type", "field.call.invite.ok");
        assertThat(result).containsEntry("status", "RINGING");
        verify(publisher).publish(eq("video.field.call.incoming"), any());
    }
}
