package com.robot.control.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.dto.IntercomResponse;
import com.robot.control.dto.IntercomStatus;
import com.robot.control.dto.VideoSessionStatus;
import com.robot.control.messaging.RobotMediaCommandService;
import com.robot.control.robot.dto.RobotCameraResponse;
import com.robot.control.robot.dto.RobotDeviceResponse;
import com.robot.control.robot.service.RobotRegistryService;
import com.robot.control.service.ControlVideoCommandService;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntercomCallServiceTest {

    private final ControlVideoCommandService videoCommandService = mock(ControlVideoCommandService.class);
    private final ControlMediaServiceClient mediaServiceClient = mock(ControlMediaServiceClient.class);
    private final RobotMediaCommandService commandService = mock(RobotMediaCommandService.class);
    private final MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
    private final RobotRegistryService registryService = mock(RobotRegistryService.class);
    private IntercomCallService service;

    @BeforeEach
    void setUp() {
        service = new IntercomCallService(
                videoCommandService, mediaServiceClient, commandService, publisher, registryService);
        when(mediaServiceClient.active()).thenReturn(List.of());
        when(registryService.find("robot-001")).thenReturn(Optional.of(robot("online")));
    }

    @Test
    void inviteAndRejectPublishesStateWithoutStartingMedia() {
        service.invite(invite("call-1"), "robot-001");

        assertThat(service.ringingCalls()).singleElement().satisfies(call -> {
            assertThat(call.get("callId")).isEqualTo("call-1");
            assertThat(call.get("robotName")).isEqualTo("巡检机器人");
            assertThat(call.get("status")).isEqualTo("RINGING");
            assertThat(call.get("expiresAtEpochMillis")).isInstanceOf(Long.class);
            assertThat(call.get("remainingSeconds")).isEqualTo(30L);
        });
        verify(publisher).publish(eq("video.intercom.call.incoming"), any());

        service.reject("call-1", operator());

        assertThat(service.ringingCalls()).isEmpty();
        ArgumentCaptor<Object> stateCaptor = ArgumentCaptor.forClass(Object.class);
        verify(commandService, org.mockito.Mockito.times(2))
                .sendIntercomCallState(eq("robot-001"), stateCaptor.capture());
        assertThat(asMap(stateCaptor.getAllValues().get(1))).containsEntry("status", "rejected");
    }

    @Test
    void acceptStartsExistingIntercomAndReturnsTokenOnlyInDirectResult() {
        IntercomResponse intercom = new IntercomResponse(
                "vs-1",
                "robot-001",
                "camera01",
                "media.robot-001.camera01.visible.sub",
                VideoSessionStatus.INIT,
                IntercomStatus.STARTING,
                true,
                "ws://livekit",
                "operator-secret-token",
                OffsetDateTime.now().plusMinutes(5));
        when(videoCommandService.startIntercom(eq("robot-001"), eq("camera01"), any(), any()))
                .thenReturn(intercom);
        service.invite(invite("call-2"), "robot-001");

        Map<String, Object> result = service.accept("call-2", operator());

        assertThat(result.get("intercom")).isEqualTo(intercom);
        assertThat(asMap(result.get("call")))
                .containsEntry("status", "ACCEPTED")
                .containsEntry("sessionId", "vs-1");
        ArgumentCaptor<Object> broadcastCaptor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq("video.intercom.call.status"), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().toString()).doesNotContain("operator-secret-token");
    }

    private IntercomCallInvite invite(String callId) {
        return new IntercomCallInvite(
                callId,
                "robot-001",
                "camera01",
                "visible",
                "sub",
                "需要人工协助",
                30,
                OffsetDateTime.now());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private CurrentUser operator() {
        return new CurrentUser("operator-1", "org001", Set.of("MEDIA_OPERATOR"), "web-1");
    }

    private RobotDeviceResponse robot(String status) {
        return new RobotDeviceResponse(
                "robot-001",
                "client-1",
                "巡检机器人",
                "轮式机器人",
                80,
                status,
                "MANUAL",
                1L,
                "IDLE",
                "IDLE",
                null,
                false,
                OffsetDateTime.now(),
                List.of(new RobotCameraResponse("camera01", "camera01", "body", "前视摄像头", "sub")),
                List.of(),
                "2026-07-20 12:00:00");
    }
}
