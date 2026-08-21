package com.robot.control.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.robot.control.auth.CurrentUser;
import com.robot.control.call.IntercomBusyException;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.control.messaging.RobotMediaCommandService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlVideoCommandServiceTest {

    private final ControlMediaServiceClient mediaServiceClient = mock(ControlMediaServiceClient.class);
    private final RobotMediaCommandService commandService = mock(RobotMediaCommandService.class);
    private final ControlManagementClient managementClient = mock(ControlManagementClient.class);
    private final ControlVideoCommandService service =
            new ControlVideoCommandService(mediaServiceClient, commandService, managementClient);

    @Test
    void blocksOperatorFromStartingSecondRobotIntercom() {
        allowRobot("robot-002");
        when(mediaServiceClient.active()).thenReturn(List.of(activeSession(
                "vs-active", "robot-001", "operator-1", "web-1")));

        assertThatThrownBy(() ->
                        service.startIntercom("robot-002", "camera01", null, operator("operator-1", "web-2")))
                .isInstanceOf(IntercomBusyException.class)
                .extracting("code")
                .isEqualTo("OPERATOR_BUSY");

        verifyNoInteractions(commandService);
    }

    @Test
    void blocksClientFromStartingSecondRobotIntercom() {
        allowRobot("robot-002");
        when(mediaServiceClient.active()).thenReturn(List.of(activeSession(
                "vs-active", "robot-001", "operator-2", "web-1")));

        assertThatThrownBy(() ->
                        service.startIntercom("robot-002", "camera01", null, operator("operator-1", "web-1")))
                .isInstanceOf(IntercomBusyException.class)
                .extracting("code")
                .isEqualTo("CLIENT_BUSY");
    }

    private CurrentUser operator(String userId, String clientId) {
        return new CurrentUser(userId, "org001", Set.of("MEDIA_OPERATOR"), clientId);
    }

    private void allowRobot(String robotId) {
        when(managementClient.deviceBySerialNumber(robotId))
                .thenReturn(Optional.of(Map.of("serialNumber", robotId)));
    }

    private VideoSessionResponse activeSession(
            String sessionId,
            String robotId,
            String operatorId,
            String clientId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new VideoSessionResponse(
                sessionId,
                robotId,
                VideoSourceType.ROBOT_CAMERA,
                robotId,
                "camera01",
                VideoChannel.visible,
                VideoQuality.sub,
                VideoSessionStatus.STREAMING,
                "media." + robotId + ".camera01.visible.sub",
                "ws://livekit",
                null,
                "track-1",
                "video.robot.main",
                1,
                IntercomStatus.ACTIVE,
                false,
                operatorId,
                clientId,
                "audio-track-1",
                "audio.robot.mic",
                null,
                null,
                now,
                now);
    }
}
