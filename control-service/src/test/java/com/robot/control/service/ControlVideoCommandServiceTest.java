package com.robot.control.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.control.auth.CurrentUser;
import com.robot.control.call.IntercomBusyException;
import com.robot.control.client.ControlManagementClient;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.dto.ControlStartVideoRequest;
import com.robot.media.common.video.CreateVideoSessionRequest;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.media.common.video.VideoStartCommand;
import com.robot.control.messaging.RobotMediaCommandService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void publishesSameStartCommandOnlyOnce() {
        VideoStartCommand command = new VideoStartCommand(
                "cmd-1",
                "vs-1",
                "robot-001",
                VideoSourceType.ROBOT_CAMERA,
                "robot-001",
                "camera01",
                VideoChannel.visible,
                VideoQuality.sub,
                "ws://livekit",
                "media.robot-001.camera01.visible.sub",
                "publisher-token",
                "robot:robot-001:camera01",
                null,
                OffsetDateTime.now().plusMinutes(10));
        when(mediaServiceClient.restartCommand("vs-1", null)).thenReturn(command);

        IntStream.range(0, 20).parallel().forEach(ignored -> service.restartSession("vs-1"));

        verify(commandService, times(1)).sendStart(command);
    }

    @Test
    void routesFixedCameraRecoveryToRestartWithoutChangingRobotStartRoute() {
        VideoStartCommand command = new VideoStartCommand(
                "cmd-fixed-1",
                "vs-fixed-1",
                "camera-001",
                VideoSourceType.FIXED_CAMERA,
                "camera-001",
                "camera-001",
                VideoChannel.visible,
                VideoQuality.sub,
                "ws://livekit",
                "media.camera-001.visible.sub",
                "publisher-token",
                "fixed-camera:camera-001",
                null,
                OffsetDateTime.now().plusMinutes(10));
        when(mediaServiceClient.restartCommand("vs-fixed-1", null)).thenReturn(command);

        service.restartSession("vs-fixed-1");

        verify(commandService).sendFixedCameraRestart(command);
        verify(commandService, times(0)).sendFixedCameraStart(command);
        verify(commandService, times(0)).sendStart(command);
    }

    @Test
    void healthRecoveryAcceptsOnlyFixedCameraCommands() {
        VideoStartCommand fixed = new VideoStartCommand(
                "cmd-fixed-health", "vs-fixed-health", "camera-001", VideoSourceType.FIXED_CAMERA,
                "camera-001", "camera-001", VideoChannel.visible, VideoQuality.sub,
                "ws://livekit", "room-fixed", "token", "fixed-camera:camera-001", null,
                OffsetDateTime.now().plusMinutes(10));
        VideoStartCommand robot = new VideoStartCommand(
                "cmd-robot-health", "vs-robot-health", "robot-001", VideoSourceType.ROBOT_CAMERA,
                "robot-001", "camera01", VideoChannel.visible, VideoQuality.sub,
                "ws://livekit", "room-robot", "token", "robot:robot-001:camera01", null,
                OffsetDateTime.now().plusMinutes(10));
        when(mediaServiceClient.fixedCameraRecoveryCommands("camera-001", false))
                .thenReturn(List.of(fixed, robot));

        service.recoverFixedCameraSources("camera-001", false);
        service.recoverFixedCameraSources("camera-001", false);

        verify(commandService, times(2)).sendFixedCameraRestart(fixed);
        verify(commandService, times(0)).sendStart(robot);
    }

    @Test
    void startsLegacyRtspCameraWithGatewayModeAndRevisionZero() {
        when(managementClient.fixedCamera("camera-001")).thenReturn(Optional.of(Map.of(
                "cameraId", "camera-001",
                "enabled", true,
                "mainStreamUrl", "rtsp://camera/main")));
        when(mediaServiceClient.createVideoSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(fixedCameraSession(VideoSessionStatus.STREAMING, VideoPublisherMode.FIXED_CAMERA_GATEWAY, 0L));

        service.startFixedCameraVideo("camera-001", null, operator("operator-1", "web-1"));

        ArgumentCaptor<CreateVideoSessionRequest> request = ArgumentCaptor.forClass(CreateVideoSessionRequest.class);
        verify(mediaServiceClient).createVideoSession(request.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(request.getValue().getExpectedPublisherMode()).isEqualTo(VideoPublisherMode.FIXED_CAMERA_GATEWAY);
        assertThat(request.getValue().getExpectedPublisherRevision()).isZero();
        verify(mediaServiceClient, never()).requestClientStart(anyString(), anyString());
    }

    @Test
    void routesRtmpCameraToIngressWithoutGatewayCommand() {
        when(managementClient.fixedCamera("camera-001")).thenReturn(Optional.of(Map.of(
                "cameraId", "camera-001",
                "enabled", true,
                "protocolType", "RTMP",
                "mediaTransitionState", "STABLE",
                "publisherRevision", 7L)));
        when(mediaServiceClient.createVideoSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(fixedCameraSession(VideoSessionStatus.INIT, VideoPublisherMode.LIVEKIT_INGRESS, 7L));
        ControlStartVideoRequest startRequest = new ControlStartVideoRequest();
        startRequest.setQuality(VideoQuality.sub);

        service.startFixedCameraVideo("camera-001", startRequest, operator("operator-1", "web-1"));

        ArgumentCaptor<CreateVideoSessionRequest> request = ArgumentCaptor.forClass(CreateVideoSessionRequest.class);
        verify(mediaServiceClient).createVideoSession(request.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(request.getValue().getExpectedPublisherMode()).isEqualTo(VideoPublisherMode.LIVEKIT_INGRESS);
        assertThat(request.getValue().getExpectedPublisherRevision()).isEqualTo(7L);
        assertThat(request.getValue().getQuality()).isEqualTo(VideoQuality.main);
        verify(mediaServiceClient, never()).requestClientStart(anyString(), anyString());
        verifyNoInteractions(commandService);
    }

    @Test
    void rejectsFixedCameraWhileMediaConfigurationIsTransitioning() {
        when(managementClient.fixedCamera("camera-001")).thenReturn(Optional.of(Map.of(
                "cameraId", "camera-001",
                "enabled", true,
                "protocolType", "RTMP",
                "mediaTransitionState", "SWITCHING_TO_RTMP",
                "publisherRevision", 7L)));

        assertThatThrownBy(() -> service.startFixedCameraVideo(
                        "camera-001", null, operator("operator-1", "web-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正在切换");

        verifyNoInteractions(mediaServiceClient, commandService);
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
                VideoPublisherMode.DEVICE_CLIENT,
                0L,
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

    private VideoSessionResponse fixedCameraSession(
            VideoSessionStatus status,
            VideoPublisherMode publisherMode,
            long publisherRevision) {
        OffsetDateTime now = OffsetDateTime.now();
        return new VideoSessionResponse(
                "vs-fixed",
                "camera-001",
                VideoSourceType.FIXED_CAMERA,
                "camera-001",
                publisherMode,
                publisherRevision,
                "camera-001",
                VideoChannel.visible,
                VideoQuality.main,
                status,
                "media.fixed.camera-001.visible.main",
                "ws://livekit",
                "viewer-token",
                null,
                null,
                1,
                IntercomStatus.IDLE,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }
}
