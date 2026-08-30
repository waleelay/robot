package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.CreateVideoSessionRequest;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VideoSessionServiceIntercomOccupancyTest {

    private final VideoSessionRepository repository = mock(VideoSessionRepository.class);
    private final MediaSessionViewerRepository viewerRepository = mock(MediaSessionViewerRepository.class);
    private final LiveKitRoomService liveKitRoomService = mock(LiveKitRoomService.class);
    private final LiveKitTokenService liveKitTokenService = mock(LiveKitTokenService.class);
    private final MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
    private final FileService fileService = mock(FileService.class);
    private final MediaTrackService mediaTrackService = mock(MediaTrackService.class);
    private final MediaProperties properties = new MediaProperties();
    private VideoSessionService service;
    private VideoSession target;

    @BeforeEach
    void setUp() {
        service = new VideoSessionService(
                repository,
                viewerRepository,
                liveKitRoomService,
                liveKitTokenService,
                publisher,
                fileService,
                mediaTrackService,
                properties);
        target = session("vs-target", "robot-002", null, null, IntercomStatus.IDLE);
        when(repository.findById("vs-target")).thenReturn(Optional.of(target));
    }

    @Test
    void blocksSameOperatorOnAnotherRobot() {
        when(repository.findByIntercomStatusIn(anyCollection())).thenReturn(List.of(
                session("vs-active", "robot-001", "operator-1", "web-2", IntercomStatus.ACTIVE)));

        assertThatThrownBy(() -> service.startIntercom("vs-target", operator("operator-1", "web-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前操作员正在与其他机器人通话，请先结束当前通话");

        verifyNoInteractions(liveKitRoomService);
    }

    @Test
    void blocksSameClientOnAnotherRobot() {
        when(repository.findByIntercomStatusIn(anyCollection())).thenReturn(List.of(
                session("vs-active", "robot-001", "operator-2", "web-1", IntercomStatus.ACTIVE)));

        assertThatThrownBy(() -> service.startIntercom("vs-target", operator("operator-1", "web-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("当前终端正在与其他机器人通话，请先结束当前通话");
    }

    @Test
    void blocksSecondSessionForSameRobot() {
        target.setRobotId("robot-001");
        when(repository.findByIntercomStatusIn(anyCollection())).thenReturn(List.of(
                session("vs-active", "robot-001", "operator-2", "web-2", IntercomStatus.ACTIVE)));

        assertThatThrownBy(() -> service.startIntercom("vs-target", operator("operator-1", "web-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("该机器人正在进行其他对讲");
    }

    @Test
    void ignoresLateStreamingStatusAfterSessionBecameIdle() {
        target.setViewerCount(0);
        target.setStatus(VideoSessionStatus.IDLE_WAIT);
        target.setIntercomStatus(IntercomStatus.IDLE);

        service.handleClientStatus(
                "vs-target",
                "streaming",
                "TR_late",
                "video.visible.sub",
                null,
                null);

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.IDLE_WAIT);
        assertThat(target.getTrackSid()).isNull();
        verifyNoInteractions(mediaTrackService);
    }

    @Test
    void heartbeatRestoresIdleSessionWhenPublishedTrackStillExists() {
        target.setStatus(VideoSessionStatus.IDLE_WAIT);
        target.setIdleSince(OffsetDateTime.now());
        target.setTrackSid("TR_existing");
        target.setTrackName("video.visible.sub");
        when(viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(
                "vs-target", "user:operator-1:web-1")).thenReturn(Optional.empty());
        when(viewerRepository.countBySessionIdAndLeftAtIsNull("vs-target")).thenReturn(1L);

        var response = service.heartbeat("vs-target", operator("operator-1", "web-1"));

        assertThat(response.status()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(response.viewerCount()).isEqualTo(1);
        assertThat(target.getIdleSince()).isNull();
        verify(publisher).publish("video.session.streaming", target);
    }

    @Test
    void reopensFixedCameraSessionWhenLiveKitHasNoActualTrack() {
        target.setSourceType(VideoSourceType.FIXED_CAMERA);
        target.setSourceId("camera-001");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.main);
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setRoomName("media.fixed.camera-001.visible.main");
        target.setTrackSid("TR_vs_placeholder");
        target.setTrackName("video.visible.main");
        when(repository.findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                any(), anyString(), anyString(), any(), any(), anyCollection())).thenReturn(Optional.of(target));
        when(liveKitRoomService.resolveActiveVideoTrackSid(target.getRoomName(), target.getTrackSid()))
                .thenReturn(Optional.empty());
        when(liveKitTokenService.createInteractiveViewerToken(anyString(), anyString(), anyString()))
                .thenReturn(new LiveKitTokenService.TokenResult("viewer-token", OffsetDateTime.now().plusMinutes(10)));
        when(viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(target.getSessionId())).thenReturn(1L);

        CreateVideoSessionRequest request = new CreateVideoSessionRequest();
        request.setReuse(true);
        request.setSourceType(VideoSourceType.FIXED_CAMERA);
        request.setSourceId("camera-001");
        request.setDeviceId("camera01");
        request.setChannel(VideoChannel.visible);
        request.setQuality(VideoQuality.main);

        var response = service.create(request, operator("operator-1", "web-1"));

        assertThat(response.status()).isEqualTo(VideoSessionStatus.INIT);
        assertThat(target.getTrackSid()).isNull();
        assertThat(target.getTrackName()).isNull();
    }

    @Test
    void fixedCameraProcessStatusWaitsForActualLiveKitTrack() {
        target.setSourceType(VideoSourceType.FIXED_CAMERA);
        target.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);

        service.handleClientStatus(
                "vs-target", "streaming", "TR_placeholder", "video.visible.sub", null, null);

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.ROOM_READY);
        assertThat(target.getTrackSid()).isNull();
        verifyNoInteractions(mediaTrackService);
    }

    @Test
    void confirmsFixedCameraOnlyAfterActualLiveKitTrackExists() {
        target.setSourceType(VideoSourceType.FIXED_CAMERA);
        target.setStatus(VideoSessionStatus.ROOM_READY);
        target.setRoomName("media.fixed.camera-001.visible.sub");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);
        when(liveKitRoomService.resolveActiveVideoTrackSid(target.getRoomName(), null))
                .thenReturn(Optional.of("TR_actual"));

        assertThat(service.confirmFixedCameraTrack("vs-target")).isTrue();
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(target.getTrackSid()).isEqualTo("TR_actual");
        verify(mediaTrackService).publish(target, "TR_actual", "video.visible.sub");
    }

    @Test
    void fixedCameraInitialProcessExitFailsInsteadOfRestartingForever() {
        target.setSourceType(VideoSourceType.FIXED_CAMERA);
        target.setStatus(VideoSessionStatus.ROOM_READY);

        service.handleClientStatus(
                "vs-target", "interrupted", null, null, "PUBLISH_PROCESS_EXITED", "推流进程退出");

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.FAILED);
        assertThat(target.getLastErrorCode()).isEqualTo("PUBLISH_PROCESS_EXITED");
    }

    @Test
    void stopDoesNotReviveClosedSession() {
        target.setStatus(VideoSessionStatus.CLOSED);

        var response = service.stop("vs-target", operator("operator-1", "web-1"));

        assertThat(response.status()).isEqualTo(VideoSessionStatus.CLOSED);
        verifyNoInteractions(viewerRepository);
    }

    @Test
    void restartsInterruptedSessionByLastClientStatusInsteadOfViewerHeartbeat() {
        target.setStatus(VideoSessionStatus.INTERRUPTED);
        target.setViewerCount(1);
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(15);
        when(repository.findByStatusAndLastStatusAtBefore(VideoSessionStatus.INTERRUPTED, threshold))
                .thenReturn(List.of(target));

        assertThat(service.interruptedRestartCandidates(threshold)).containsExactly("vs-target");
        verify(repository, never()).findByStatusAndUpdatedAtBefore(VideoSessionStatus.INTERRUPTED, threshold);
    }

    @Test
    void startsRecordingWithActualLiveKitTrackSid() {
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setRoomName("media.robot-002.camera01.visible.auto");
        target.setTrackSid("TR_vs_placeholder");
        CurrentUser user = operator("operator-1", "web-1");
        when(liveKitRoomService.resolveActiveVideoTrackSid(target.getRoomName(), target.getTrackSid()))
                .thenReturn(Optional.of("TR_actual"));

        service.startRecording("vs-target", user);

        verify(fileService).startLiveRecording(target, "TR_actual", user);
    }

    private CurrentUser operator(String userId, String clientId) {
        return new CurrentUser(userId, "org001", Set.of("MEDIA_OPERATOR"), clientId);
    }

    private VideoSession session(
            String sessionId,
            String robotId,
            String operatorId,
            String clientId,
            IntercomStatus status) {
        VideoSession session = new VideoSession();
        session.setSessionId(sessionId);
        session.setRobotId(robotId);
        session.setDeviceId("camera01");
        session.setIntercomStatus(status);
        session.setIntercomOperatorId(operatorId);
        session.setIntercomClientId(clientId);
        return session;
    }
}
