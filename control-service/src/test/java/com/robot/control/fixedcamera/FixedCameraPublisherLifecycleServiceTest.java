package com.robot.control.fixedcamera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.messaging.RobotMediaCommandService;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.FixedCameraPublisherPresenceResponse;
import com.robot.media.common.video.FixedCameraPublisherStopCommand;
import com.robot.media.common.video.VideoPublisherMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixedCameraPublisherLifecycleServiceTest {

    @Test
    void switchesModeSendsExactStopsAndConfirmsPublisherExit() {
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        RobotMediaCommandService commands = mock(RobotMediaCommandService.class);
        FixedCameraPublisherLifecycleService service = new FixedCameraPublisherLifecycleService(media, commands);
        FixedCameraPublisherStopCommand stop = new FixedCameraPublisherStopCommand(
                "cmd-1", "session-1", "camera-001", "room-1");
        FixedCameraPublisherModeResponse response = new FixedCameraPublisherModeResponse(
                "camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 4L, List.of(stop));
        when(media.switchFixedCameraPublisherMode(
                "camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 4L)).thenReturn(response);
        when(media.fixedCameraPublisherPresence("camera-001")).thenReturn(
                new FixedCameraPublisherPresenceResponse(
                        "camera-001", false, false, OffsetDateTime.now()));

        assertThat(service.switchMode("camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 4L))
                .isSameAs(response);

        verify(commands).sendFixedCameraStop(stop);
        verify(media).fixedCameraPublisherPresence("camera-001");
    }

    @Test
    void switchingToGatewayDoesNotCreateGatewayStartCommand() {
        ControlMediaServiceClient media = mock(ControlMediaServiceClient.class);
        RobotMediaCommandService commands = mock(RobotMediaCommandService.class);
        FixedCameraPublisherLifecycleService service = new FixedCameraPublisherLifecycleService(media, commands);
        FixedCameraPublisherModeResponse response = new FixedCameraPublisherModeResponse(
                "camera-001", VideoPublisherMode.FIXED_CAMERA_GATEWAY, 5L, List.of());
        when(media.switchFixedCameraPublisherMode(
                "camera-001", VideoPublisherMode.FIXED_CAMERA_GATEWAY, 5L)).thenReturn(response);
        when(media.fixedCameraPublisherPresence("camera-001")).thenReturn(
                new FixedCameraPublisherPresenceResponse(
                        "camera-001", false, false, OffsetDateTime.now()));

        service.switchMode("camera-001", VideoPublisherMode.FIXED_CAMERA_GATEWAY, 5L);

        verify(media).fixedCameraPublisherPresence("camera-001");
        org.mockito.Mockito.verifyNoInteractions(commands);
    }
}
