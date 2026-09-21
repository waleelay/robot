package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.robot.media.common.video.CreateVideoSessionRequest;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoPublisherMode;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.auth.CurrentUser;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitRoomService.ActiveVideoTrack;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.livekit.LiveKitTokenService.TokenResult;
import com.robot.mediaserver.video.model.FixedCameraStreamStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class VideoSessionServiceIngressLifecycleTest {

    private final VideoSessionRepository sessionRepository = mock(VideoSessionRepository.class);
    private final VideoSourceRuntimeRepository runtimeRepository = mock(VideoSourceRuntimeRepository.class);
    private final MediaSessionViewerRepository viewerRepository = mock(MediaSessionViewerRepository.class);
    private final LiveKitRoomService roomService = mock(LiveKitRoomService.class);
    private final LiveKitTokenService tokenService = mock(LiveKitTokenService.class);
    private final FileService fileService = mock(FileService.class);
    private final MediaTrackService trackService = mock(MediaTrackService.class);
    private final VideoSourceRuntime runtime = ingressRuntime();
    private final MediaProperties properties = new MediaProperties();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private VideoSessionService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        service = new VideoSessionService(
                sessionRepository,
                runtimeRepository,
                viewerRepository,
                roomService,
                tokenService,
                mock(MediaWebSocketPublisher.class),
                fileService,
                trackService,
                properties,
                transactionManager,
                mock(EntityManager.class));
    }

    @Test
    void createsStreamingSessionFromAlreadyOnlineIngressTrack() {
        prepareOnlineIngressRuntime();
        when(runtimeRepository.findBySourceForUpdate(
                VideoSourceType.FIXED_CAMERA,
                "camera-001",
                "camera-001",
                VideoChannel.visible,
                VideoQuality.main)).thenReturn(Optional.of(runtime));
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(any())).thenReturn(1L);
        when(tokenService.createViewerToken(any(), any(), any()))
                .thenReturn(new TokenResult("viewer-token", OffsetDateTime.now().plusMinutes(10)));

        VideoSessionResponse response = service.create(ingressRequest(false), viewer());

        assertThat(response.status()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(response.trackSid()).isEqualTo("TR_001");
        assertThat(response.trackName()).isEqualTo("camera");
        assertThat(response.viewerToken()).isEqualTo("viewer-token");
        verify(trackService).publish(any(VideoSession.class),
                org.mockito.ArgumentMatchers.eq("fixed-camera:camera-001"),
                org.mockito.ArgumentMatchers.eq("TR_001"),
                org.mockito.ArgumentMatchers.eq("camera"));
    }

    @Test
    void reusesInterruptedSessionFromAlreadyOnlineIngressTrack() {
        prepareOnlineIngressRuntime();
        VideoSession session = session();
        session.setStatus(VideoSessionStatus.INTERRUPTED);
        session.setTrackSid(null);
        session.setTrackName(null);
        when(runtimeRepository.findBySourceForUpdate(
                VideoSourceType.FIXED_CAMERA,
                "camera-001",
                "camera-001",
                VideoChannel.visible,
                VideoQuality.main)).thenReturn(Optional.of(runtime));
        when(sessionRepository.findFirstByRuntimeIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(session));
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(session.getSessionId())).thenReturn(1L);
        when(tokenService.createViewerToken(any(), any(), any()))
                .thenReturn(new TokenResult("viewer-token", OffsetDateTime.now().plusMinutes(10)));

        VideoSessionResponse response = service.create(ingressRequest(true), viewer());

        assertThat(response.status()).isEqualTo(VideoSessionStatus.STREAMING);
        assertThat(response.trackSid()).isEqualTo("TR_001");
        verify(trackService).publish(session, "fixed-camera:camera-001", "TR_001", "camera");
    }

    @Test
    void reconcilesIngressRuntimeWithoutActiveVideoSession() {
        when(sessionRepository.findDistinctRuntimeIdsByStatusIn(any())).thenReturn(List.of());
        when(runtimeRepository.findByPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS))
                .thenReturn(List.of(runtime));
        when(runtimeRepository.findById(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(runtimeRepository.findByIdForUpdate(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(sessionRepository.findByRuntimeIdOrderBySessionIdAsc(runtime.getRuntimeId())).thenReturn(List.of());
        when(roomService.resolveActiveVideoTrack(
                runtime.getRoomName(), "fixed-camera:camera-001", null))
                .thenReturn(Optional.of(new ActiveVideoTrack(
                        "fixed-camera:camera-001", "PA_001", "TR_001", "camera")));

        service.reconcileLiveKitTracks();

        assertThat(runtime.getLastStreamStatus()).isEqualTo(FixedCameraStreamStatus.ONLINE);
        assertThat(runtime.getLastReasonCode()).isNull();
        assertThat(runtime.getTrackSid()).isEqualTo("TR_001");
        assertThat(runtime.getLastVerifiedAt()).isNotNull();
        verify(runtimeRepository).save(runtime);
    }

    @Test
    void quiesceStopsRecordingAndClosesIngressSessions() {
        VideoSession session = session();
        when(runtimeRepository.findBySourceTypeAndSourceIdAndDeviceIdAndChannelAndQuality(
                VideoSourceType.FIXED_CAMERA,
                "camera-001",
                "camera-001",
                VideoChannel.visible,
                VideoQuality.main))
                .thenReturn(Optional.of(runtime));
        when(runtimeRepository.findByIdForUpdate(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(sessionRepository.findByRuntimeIdOrderBySessionIdAsc(runtime.getRuntimeId()))
                .thenReturn(List.of(session));
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(session.getSessionId())).thenReturn(0L);

        service.quiesceLiveKitIngress("camera-001");

        verify(fileService).stopActiveLiveRecordingForSession(session.getSessionId());
        verify(viewerRepository).closeActiveLeasesBySessionId(any(), any());
        verify(trackService).unpublish(session);
        verify(roomService).deleteRoom(runtime.getRoomName());
        assertThat(session.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
    }

    @Test
    void excludesIngressRuntimeFromClientRestartCandidates() {
        VideoSession session = session();
        session.setStatus(VideoSessionStatus.INTERRUPTED);
        session.setViewerCount(1);
        when(sessionRepository.findByStatusAndLastStatusAtBefore(
                any(VideoSessionStatus.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(session));
        when(runtimeRepository.findById(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));

        assertThat(service.interruptedRestartCandidates(OffsetDateTime.now())).isEmpty();
    }

    @Test
    void switchesGatewayRuntimeToIngressAndReturnsExactStopCommand() {
        runtime.setPublisherMode(VideoPublisherMode.FIXED_CAMERA_GATEWAY);
        runtime.setPublisherRevision(3L);
        VideoSession session = session();
        when(runtimeRepository.findBySourceTypeAndSourceIdOrderByRuntimeIdAsc(
                VideoSourceType.FIXED_CAMERA, "camera-001")).thenReturn(List.of(runtime));
        when(runtimeRepository.findByIdForUpdate(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(sessionRepository.findByRuntimeIdOrderBySessionIdAsc(runtime.getRuntimeId()))
                .thenReturn(List.of(session));

        var response = service.switchFixedCameraPublisherMode(
                "camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 4L);

        assertThat(response.publisherMode()).isEqualTo(VideoPublisherMode.LIVEKIT_INGRESS);
        assertThat(response.publisherRevision()).isEqualTo(4L);
        assertThat(response.stopCommands()).singleElement().satisfies(command -> {
            assertThat(command.cameraId()).isEqualTo("camera-001");
            assertThat(command.sessionId()).isEqualTo("vs-fixed");
        });
        assertThat(runtime.getPublisherMode()).isEqualTo(VideoPublisherMode.LIVEKIT_INGRESS);
        assertThat(session.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        verify(roomService).deleteRoom(runtime.getRoomName());
        verify(transactionManager, times(3)).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void sameTargetModeOnlyAdvancesGenerationWithoutClosingCurrentSession() {
        runtime.setPublisherRevision(3L);
        VideoSession session = session();
        when(runtimeRepository.findBySourceTypeAndSourceIdOrderByRuntimeIdAsc(
                VideoSourceType.FIXED_CAMERA, "camera-001")).thenReturn(List.of(runtime));
        when(runtimeRepository.findByIdForUpdate(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(sessionRepository.findByRuntimeIdOrderBySessionIdAsc(runtime.getRuntimeId()))
                .thenReturn(List.of(session));

        service.switchFixedCameraPublisherMode("camera-001", VideoPublisherMode.LIVEKIT_INGRESS, 4L);

        assertThat(runtime.getPublisherRevision()).isEqualTo(4L);
        assertThat(session.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        verify(roomService, never()).deleteRoom(runtime.getRoomName());
    }

    @Test
    void usesConfiguredIngressStatusStalenessWhenLiveKitIsUnavailable() {
        properties.getLivekit().setIngressStatusStaleSeconds(5);
        runtime.setLastStreamStatus(FixedCameraStreamStatus.ONLINE);
        runtime.setLastVerifiedAt(OffsetDateTime.now().minusSeconds(10));
        when(sessionRepository.findDistinctRuntimeIdsByStatusIn(any())).thenReturn(List.of());
        when(runtimeRepository.findByPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS))
                .thenReturn(List.of(runtime));
        when(runtimeRepository.findById(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(runtimeRepository.findByIdForUpdate(runtime.getRuntimeId())).thenReturn(Optional.of(runtime));
        when(sessionRepository.findByRuntimeIdOrderBySessionIdAsc(runtime.getRuntimeId())).thenReturn(List.of());
        when(roomService.resolveActiveVideoTrack(runtime.getRoomName(), "fixed-camera:camera-001", null))
                .thenThrow(new IllegalStateException("LiveKit unavailable"));

        service.reconcileLiveKitTracks();

        assertThat(runtime.getLastStreamStatus()).isEqualTo(FixedCameraStreamStatus.UNKNOWN);
        assertThat(runtime.getLastReasonCode()).isEqualTo("LIVEKIT_STATUS_STALE");
        verify(runtimeRepository).save(runtime);
    }

    private VideoSourceRuntime ingressRuntime() {
        VideoSourceRuntime value = new VideoSourceRuntime();
        value.setRuntimeId("runtime-fixed");
        value.setSourceType(VideoSourceType.FIXED_CAMERA);
        value.setSourceId("camera-001");
        value.setDeviceId("camera-001");
        value.setChannel(VideoChannel.visible);
        value.setQuality(VideoQuality.main);
        value.setRoomName("media.fixed.camera-001.visible.main");
        value.setPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS);
        value.setIngressId("IN_001");
        value.setCreatedAt(OffsetDateTime.now());
        value.setUpdatedAt(OffsetDateTime.now());
        return value;
    }

    private void prepareOnlineIngressRuntime() {
        runtime.setLastStreamStatus(FixedCameraStreamStatus.ONLINE);
        runtime.setPublisherIdentity("fixed-camera:camera-001");
        runtime.setPublisherParticipantSid("PA_001");
        runtime.setTrackSid("TR_001");
        runtime.setTrackName("camera");
        runtime.setLastVerifiedAt(OffsetDateTime.now());
    }

    private CreateVideoSessionRequest ingressRequest(boolean reuse) {
        CreateVideoSessionRequest request = new CreateVideoSessionRequest();
        request.setRobotId("camera-001");
        request.setSourceType(VideoSourceType.FIXED_CAMERA);
        request.setSourceId("camera-001");
        request.setDeviceId("camera-001");
        request.setChannel(VideoChannel.visible);
        request.setQuality(VideoQuality.main);
        request.setExpectedPublisherMode(VideoPublisherMode.LIVEKIT_INGRESS);
        request.setExpectedPublisherRevision(0L);
        request.setReuse(reuse);
        return request;
    }

    private CurrentUser viewer() {
        return new CurrentUser("viewer-001", "org-001", Set.of("MEDIA_VIEWER"), "browser-001");
    }

    private VideoSession session() {
        VideoSession value = new VideoSession();
        value.setSessionId("vs-fixed");
        value.setRuntimeId(runtime.getRuntimeId());
        value.setRobotId("camera-001");
        value.setSourceType(VideoSourceType.FIXED_CAMERA);
        value.setSourceId("camera-001");
        value.setDeviceId("camera-001");
        value.setChannel(VideoChannel.visible);
        value.setQuality(VideoQuality.main);
        value.setRoomName(runtime.getRoomName());
        value.setStatus(VideoSessionStatus.STREAMING);
        value.setTrackSid("TR_001");
        value.setTrackName("camera");
        value.setCreatedAt(OffsetDateTime.now());
        value.setUpdatedAt(OffsetDateTime.now());
        return value;
    }
}
