package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.robot.mediaserver.video.model.MediaSessionViewer;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;

class VideoSessionServiceIntercomOccupancyTest {

    private final VideoSessionRepository repository = mock(VideoSessionRepository.class);
    private final VideoSourceRuntimeRepository sourceRuntimeRepository = mock(VideoSourceRuntimeRepository.class);
    private final MediaSessionViewerRepository viewerRepository = mock(MediaSessionViewerRepository.class);
    private final LiveKitRoomService liveKitRoomService = mock(LiveKitRoomService.class);
    private final LiveKitTokenService liveKitTokenService = mock(LiveKitTokenService.class);
    private final MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
    private final FileService fileService = mock(FileService.class);
    private final MediaTrackService mediaTrackService = mock(MediaTrackService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final MediaProperties properties = new MediaProperties();
    private VideoSessionService service;
    private VideoSession target;

    @BeforeEach
    void setUp() {
        service = new VideoSessionService(
                repository,
                sourceRuntimeRepository,
                viewerRepository,
                liveKitRoomService,
                liveKitTokenService,
                publisher,
                fileService,
                mediaTrackService,
                properties,
                transactionManager,
                entityManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(sourceRuntimeRepository.insertIfAbsent(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(sourceRuntimeRepository.findBySourceForUpdate(any(), anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    VideoSourceRuntime runtime = new VideoSourceRuntime();
                    runtime.setRuntimeId("runtime-test");
                    runtime.setSourceType(invocation.getArgument(0));
                    runtime.setSourceId(invocation.getArgument(1));
                    runtime.setDeviceId(invocation.getArgument(2));
                    runtime.setChannel(invocation.getArgument(3));
                    runtime.setQuality(invocation.getArgument(4));
                    runtime.setRoomName("room-runtime-test");
                    return Optional.of(runtime);
                });
        target = session("vs-target", "robot-002", null, null, IntercomStatus.IDLE);
        when(repository.findById("vs-target")).thenReturn(Optional.of(target));
        when(repository.findByIdForUpdate("vs-target")).thenReturn(Optional.of(target));
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
    void ignoresLateActiveStatusAfterIntercomWasReleased() {
        target.setIntercomStatus(IntercomStatus.INTERRUPTED);

        service.handleIntercomStatus(
                "vs-target", "active", "TR_audio", "audio.robot.mic", null, null);

        assertThat(target.getIntercomStatus()).isEqualTo(IntercomStatus.INTERRUPTED);
        assertThat(target.getRobotAudioTrackSid()).isNull();
        verify(repository, never()).save(target);
    }

    @Test
    void includesOccupiedSessionWithoutHeartbeatInTimeoutCandidates() {
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(15);
        VideoSession missingHeartbeat = session(
                "vs-missing-heartbeat", "robot-001", null, null, IntercomStatus.ACTIVE);
        when(repository.findIntercomTimeoutCandidates(anyCollection(), eq(threshold)))
                .thenReturn(List.of(missingHeartbeat));

        assertThat(service.intercomTimeoutCandidates(threshold)).containsExactly("vs-missing-heartbeat");
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
        when(liveKitRoomService.resolveActiveVideoTrack(
                "room-runtime-test", "robot:robot-002:camera01", target.getTrackSid()))
                .thenReturn(Optional.of(new LiveKitRoomService.ActiveVideoTrack(
                        "robot:robot-002:camera01", "PA_robot", "TR_existing", "video.visible.sub")));
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
    void createsSessionOwnedByLockedSourceRuntime() {
        when(viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(anyString())).thenReturn(1L);
        when(liveKitTokenService.createInteractiveViewerToken(anyString(), anyString(), anyString()))
                .thenReturn(new LiveKitTokenService.TokenResult(
                        "viewer-token", OffsetDateTime.now().plusMinutes(10)));

        CreateVideoSessionRequest request = new CreateVideoSessionRequest();
        request.setReuse(true);
        request.setRobotId("robot-001");
        request.setSourceType(VideoSourceType.ROBOT_CAMERA);
        request.setSourceId("robot-001");
        request.setDeviceId("camera01");
        request.setChannel(VideoChannel.visible);
        request.setQuality(VideoQuality.sub);

        var response = service.create(request, operator("operator-1", "web-1"));

        ArgumentCaptor<VideoSession> sessionCaptor = ArgumentCaptor.forClass(VideoSession.class);
        verify(repository, times(2)).save(sessionCaptor.capture());
        VideoSession created = sessionCaptor.getValue();
        assertThat(created.getRuntimeId()).isEqualTo("runtime-test");
        assertThat(created.getRoomName()).isEqualTo("room-runtime-test");
        assertThat(response.sessionId()).isEqualTo(created.getSessionId());
        verify(sourceRuntimeRepository).insertIfAbsent(
                anyString(), eq("ROBOT_CAMERA"), eq("robot-001"), eq("camera01"),
                eq("visible"), eq("sub"), eq("media.robot-001.camera01.visible.sub"), any());
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
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "fixed-camera:camera-001", target.getTrackSid()))
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
        assertThat(target.getRuntimeId()).isEqualTo("runtime-test");
        assertThat(target.getRoomName()).isEqualTo("room-runtime-test");
        assertThat(target.getTrackSid()).isNull();
        assertThat(target.getTrackName()).isNull();
    }

    @Test
    void reusesInterruptedRobotSessionButClearsStaleTrack() {
        target.setSourceType(VideoSourceType.ROBOT_CAMERA);
        target.setSourceId("robot-002");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);
        target.setStatus(VideoSessionStatus.INTERRUPTED);
        target.setRoomName("media.robot-002.camera01.visible.sub");
        target.setTrackSid("TR_stale");
        target.setTrackName("video.visible.sub");
        when(repository.findFirstBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityAndStatusInOrderByCreatedAtDesc(
                any(), anyString(), anyString(), any(), any(), anyCollection())).thenReturn(Optional.of(target));
        when(liveKitTokenService.createInteractiveViewerToken(anyString(), anyString(), anyString()))
                .thenReturn(new LiveKitTokenService.TokenResult(
                        "viewer-token", OffsetDateTime.now().plusMinutes(10)));
        when(viewerRepository.findFirstBySessionIdAndParticipantIdentityAndLeftAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(target.getSessionId())).thenReturn(1L);

        CreateVideoSessionRequest request = new CreateVideoSessionRequest();
        request.setReuse(true);
        request.setRobotId("robot-002");
        request.setSourceType(VideoSourceType.ROBOT_CAMERA);
        request.setSourceId("robot-002");
        request.setDeviceId("camera01");
        request.setChannel(VideoChannel.visible);
        request.setQuality(VideoQuality.sub);

        var response = service.create(request, operator("operator-1", "web-1"));

        assertThat(response.sessionId()).isEqualTo("vs-target");
        assertThat(response.status()).isEqualTo(VideoSessionStatus.INIT);
        assertThat(target.getRuntimeId()).isEqualTo("runtime-test");
        assertThat(target.getRoomName()).isEqualTo("room-runtime-test");
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
    void robotProcessStatusAlsoWaitsForActualLiveKitTrack() {
        target.setSourceType(VideoSourceType.ROBOT_CAMERA);
        target.setStatus(VideoSessionStatus.REQUESTING_CLIENT);

        service.handleClientStatus(
                "vs-target", "streaming", "TR_reported", "video.visible.sub", null, null);

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.ROOM_READY);
        assertThat(target.getTrackSid()).isNull();
        verifyNoInteractions(mediaTrackService);
    }

    @Test
    void lateDeviceProcessStatusCannotOverrideConfirmedStreamingFact() {
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setTrackSid("TR_actual");
        target.setTrackName("video.visible.sub");

        service.handleClientStatus("vs-target", "room_ready", null, null, null, null);
        service.handleClientStatus("vs-target", "failed", null, null, "PROCESS_EXITED", "publisher exited");

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(target.getTrackSid()).isEqualTo("TR_actual");
        verify(publisher).publish(eq("video.client.failed"), any());
        verify(mediaTrackService, never()).unpublish(target);
    }

    @Test
    void confirmsFixedCameraOnlyAfterActualLiveKitTrackExists() {
        target.setSourceType(VideoSourceType.FIXED_CAMERA);
        target.setStatus(VideoSessionStatus.ROOM_READY);
        target.setRoomName("media.fixed.camera-001.visible.sub");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "fixed-camera:robot-002", null))
                .thenReturn(Optional.of(new LiveKitRoomService.ActiveVideoTrack(
                        "fixed-camera:robot-002", "PA_fixed", "TR_actual", "video.visible.sub")));

        assertThat(service.confirmFixedCameraTrack("vs-target")).isTrue();
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(target.getTrackSid()).isEqualTo("TR_actual");
        verify(mediaTrackService).publish(
                target, "fixed-camera:robot-002", "TR_actual", "video.visible.sub");
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
    void heartbeatDoesNotReviveClosedSession() {
        target.setStatus(VideoSessionStatus.CLOSED);

        assertThatThrownBy(() -> service.heartbeat("vs-target", operator("operator-1", "web-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("视频会话已关闭");

        verifyNoInteractions(viewerRepository);
    }

    @Test
    void stopDoesNotReviveSessionClosedWhileWaitingForRuntimeLock() {
        VideoSession staleSnapshot = session(
                "vs-target", "robot-001", null, null, IntercomStatus.IDLE);
        staleSnapshot.setStatus(VideoSessionStatus.STREAMING);
        target.setStatus(VideoSessionStatus.CLOSED);
        when(repository.findById("vs-target")).thenReturn(Optional.of(staleSnapshot));

        var response = service.stop("vs-target", operator("operator-1", "web-1"));

        assertThat(response.status()).isEqualTo(VideoSessionStatus.CLOSED);
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
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
        verify(repository, never()).findByStatusAndCommandRequestedAtBefore(VideoSessionStatus.INTERRUPTED, threshold);
    }

    @Test
    void restartsInterruptedSessionHeldOnlyByRecording() {
        target.setStatus(VideoSessionStatus.INTERRUPTED);
        target.setViewerCount(0);
        OffsetDateTime threshold = OffsetDateTime.now().minusSeconds(15);
        when(repository.findByStatusAndLastStatusAtBefore(VideoSessionStatus.INTERRUPTED, threshold))
                .thenReturn(List.of(target));
        when(fileService.hasActiveLiveRecording("vs-target")).thenReturn(true);

        assertThat(service.interruptedRestartCandidates(threshold)).containsExactly("vs-target");
    }

    @Test
    void reusesStartCommandWhileWaitingForTrack() {
        target.setViewerCount(1);
        target.setStatus(VideoSessionStatus.INIT);
        target.setRoomName("media.robot-002.camera01.visible.sub");
        target.setSourceType(VideoSourceType.ROBOT_CAMERA);
        target.setSourceId("robot-002");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);
        when(liveKitTokenService.createPublisherToken(anyString(), anyString(), anyString()))
                .thenReturn(new LiveKitTokenService.TokenResult(
                        "publisher-token", OffsetDateTime.now().plusMinutes(10)));

        var first = service.requestClientStart("vs-target", "video.client.requested");
        var second = service.requestClientStart("vs-target", "video.session.restart");

        assertThat(second.commandId()).isEqualTo(first.commandId());
        verify(liveKitRoomService, times(1)).createRoom(target.getRoomName());
    }

    @Test
    void restartDoesNotChangeViewerOccupancy() {
        target.setViewerCount(1);
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setRoomName("media.robot-002.camera01.visible.sub");
        target.setSourceType(VideoSourceType.ROBOT_CAMERA);
        target.setSourceId("robot-002");
        target.setChannel(VideoChannel.visible);
        target.setQuality(VideoQuality.sub);
        when(liveKitTokenService.createPublisherToken(anyString(), anyString(), anyString()))
                .thenReturn(new LiveKitTokenService.TokenResult(
                        "publisher-token", OffsetDateTime.now().plusMinutes(10)));

        service.restartSessionCommand("vs-target", operator("operator-1", "web-1"));

        assertThat(target.getViewerCount()).isEqualTo(1);
        verifyNoInteractions(viewerRepository);
    }

    @Test
    void staleViewerSweepDoesNotUseOneCrossViewerTransaction() throws NoSuchMethodException {
        assertThat(VideoSessionService.class.getMethod("sweepStaleViewers")
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void staleViewerSweepSkipsViewerRenewedAfterCandidateQuery() {
        MediaSessionViewer viewer = new MediaSessionViewer();
        viewer.setId("viewer-1");
        viewer.setSessionId("vs-target");
        viewer.setUserId("operator-1");
        viewer.setClientId("web-1");
        when(viewerRepository.findByLeftAtIsNullAndLastHeartbeatAtBefore(any()))
                .thenReturn(List.of(viewer));
        when(viewerRepository.closeIfStale(eq("viewer-1"), any(), any())).thenReturn(0);

        service.sweepStaleViewers();

        verify(repository, never()).save(any());
        verifyNoInteractions(fileService);
    }

    @Test
    void publishTimeoutUsesCommandClockAndRejectsOldCommandTask() {
        target.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
        target.setCommandId("cmd-current");
        target.setCommandRequestedAt(OffsetDateTime.now().minusSeconds(30));
        target.setUpdatedAt(OffsetDateTime.now());

        service.markTimeout("vs-target", "cmd-old", "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时");
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.REQUESTING_CLIENT);

        service.markTimeout("vs-target", "cmd-current", "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时");
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.FAILED);
    }

    @Test
    void startsRecordingWithActualLiveKitTrackSid() {
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setRoomName("media.robot-002.camera01.visible.auto");
        target.setRuntimeId("runtime-test");
        target.setTrackSid("TR_vs_placeholder");
        VideoSourceRuntime runtime = new VideoSourceRuntime();
        runtime.setRuntimeId("runtime-test");
        runtime.setRoomName(target.getRoomName());
        when(sourceRuntimeRepository.findByIdForUpdate("runtime-test")).thenReturn(Optional.of(runtime));
        CurrentUser user = operator("operator-1", "web-1");
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "robot:robot-002:camera01", target.getTrackSid()))
                .thenReturn(Optional.of(new LiveKitRoomService.ActiveVideoTrack(
                        "robot:robot-002:camera01", "PA_robot", "TR_actual", "video.visible.auto")));

        service.startRecording("vs-target", user);

        verify(fileService).startLiveRecording(target, "TR_actual", user);
    }

    @Test
    void reconcilesExpectedPublisherTrackIntoRuntimeAndSession() {
        target.setRuntimeId("runtime-test");
        target.setStatus(VideoSessionStatus.ROOM_READY);
        VideoSourceRuntime runtime = runtime();
        when(sourceRuntimeRepository.findById("runtime-test")).thenReturn(Optional.of(runtime));
        when(sourceRuntimeRepository.findByIdForUpdate("runtime-test")).thenReturn(Optional.of(runtime));
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-test")).thenReturn(List.of(target));
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "robot:robot-002:camera01", null))
                .thenReturn(Optional.of(new LiveKitRoomService.ActiveVideoTrack(
                        "robot:robot-002:camera01", "PA_robot", "TR_actual", "video.visible.sub")));

        service.reconcileLiveKitRuntime("runtime-test");

        assertThat(runtime.getPublisherIdentity()).isEqualTo("robot:robot-002:camera01");
        assertThat(runtime.getPublisherParticipantSid()).isEqualTo("PA_robot");
        assertThat(runtime.getTrackSid()).isEqualTo("TR_actual");
        assertThat(runtime.getLastMediaAt()).isNotNull();
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(target.getTrackSid()).isEqualTo("TR_actual");
        verify(mediaTrackService).publish(
                target, "robot:robot-002:camera01", "TR_actual", "video.visible.sub");
    }

    @Test
    void doesNotRewriteStablePublishedTrackOnPeriodicReconcile() {
        target.setRuntimeId("runtime-test");
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setTrackSid("TR_actual");
        target.setTrackName("video.visible.sub");
        VideoSourceRuntime runtime = runtime();
        runtime.setPublisherIdentity("robot:robot-002:camera01");
        runtime.setPublisherParticipantSid("PA_robot");
        runtime.setTrackSid("TR_actual");
        runtime.setTrackName("video.visible.sub");
        when(sourceRuntimeRepository.findById("runtime-test")).thenReturn(Optional.of(runtime));
        when(sourceRuntimeRepository.findByIdForUpdate("runtime-test")).thenReturn(Optional.of(runtime));
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-test")).thenReturn(List.of(target));
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "robot:robot-002:camera01", "TR_actual"))
                .thenReturn(Optional.of(new LiveKitRoomService.ActiveVideoTrack(
                        "robot:robot-002:camera01", "PA_robot", "TR_actual", "video.visible.sub")));

        service.reconcileLiveKitRuntime("runtime-test");

        verify(sourceRuntimeRepository, never()).save(runtime);
        verify(repository, never()).save(target);
        verifyNoInteractions(mediaTrackService, publisher);
    }

    @Test
    void marksStreamingSessionInterruptedOnlyAfterRoomFactIsMissing() {
        target.setRuntimeId("runtime-test");
        target.setStatus(VideoSessionStatus.STREAMING);
        target.setTrackSid("TR_old");
        target.setTrackName("video.visible.sub");
        VideoSourceRuntime runtime = runtime();
        runtime.setPublisherIdentity("robot:robot-002:camera01");
        runtime.setPublisherParticipantSid("PA_old");
        runtime.setTrackSid("TR_old");
        when(sourceRuntimeRepository.findById("runtime-test")).thenReturn(Optional.of(runtime));
        when(sourceRuntimeRepository.findByIdForUpdate("runtime-test")).thenReturn(Optional.of(runtime));
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-test")).thenReturn(List.of(target));
        when(viewerRepository.countBySessionIdAndLeftAtIsNull("vs-target")).thenReturn(1L);
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "robot:robot-002:camera01", "TR_old"))
                .thenReturn(Optional.empty());

        service.reconcileLiveKitRuntime("runtime-test");

        assertThat(runtime.getPublisherIdentity()).isNull();
        assertThat(runtime.getTrackSid()).isNull();
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.INTERRUPTED);
        assertThat(target.getTrackSid()).isNull();
        assertThat(target.getLastStatusAt()).isNotNull();
        verify(mediaTrackService).unpublish(target);
    }

    @Test
    void clearsMissingTrackWithoutLeavingIdleWait() {
        target.setRuntimeId("runtime-test");
        target.setStatus(VideoSessionStatus.IDLE_WAIT);
        target.setTrackSid("TR_old");
        target.setTrackName("video.visible.sub");
        VideoSourceRuntime runtime = runtime();
        runtime.setPublisherIdentity("robot:robot-002:camera01");
        runtime.setPublisherParticipantSid("PA_old");
        runtime.setTrackSid("TR_old");
        when(sourceRuntimeRepository.findById("runtime-test")).thenReturn(Optional.of(runtime));
        when(sourceRuntimeRepository.findByIdForUpdate("runtime-test")).thenReturn(Optional.of(runtime));
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-test")).thenReturn(List.of(target));
        when(liveKitRoomService.resolveActiveVideoTrack(
                target.getRoomName(), "robot:robot-002:camera01", "TR_old"))
                .thenReturn(Optional.empty());

        service.reconcileLiveKitRuntime("runtime-test");

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.IDLE_WAIT);
        assertThat(target.getTrackSid()).isNull();
        verify(mediaTrackService).unpublish(target);
    }

    private VideoSourceRuntime runtime() {
        VideoSourceRuntime runtime = new VideoSourceRuntime();
        runtime.setRuntimeId("runtime-test");
        runtime.setSourceType(VideoSourceType.ROBOT_CAMERA);
        runtime.setSourceId("robot-002");
        runtime.setDeviceId("camera01");
        runtime.setChannel(VideoChannel.visible);
        runtime.setQuality(VideoQuality.sub);
        runtime.setRoomName(target.getRoomName());
        runtime.setCreatedAt(OffsetDateTime.now());
        runtime.setUpdatedAt(OffsetDateTime.now());
        return runtime;
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
        session.setSourceType(VideoSourceType.ROBOT_CAMERA);
        session.setSourceId(robotId);
        session.setDeviceId("camera01");
        session.setChannel(VideoChannel.visible);
        session.setQuality(VideoQuality.sub);
        session.setRoomName("media." + robotId + ".camera01.visible.sub");
        session.setStatus(VideoSessionStatus.INIT);
        session.setIntercomStatus(status);
        session.setIntercomOperatorId(operatorId);
        session.setIntercomClientId(clientId);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return session;
    }
}
