package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.media.common.video.IntercomStatus;
import com.robot.media.common.video.VideoChannel;
import com.robot.media.common.video.VideoQuality;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.file.service.FileService;
import com.robot.mediaserver.livekit.LiveKitRoomService;
import com.robot.mediaserver.livekit.LiveKitTokenService;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.robot.mediaserver.video.repository.MediaSessionViewerRepository;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.repository.VideoSourceRuntimeRepository;
import com.robot.mediaserver.ws.MediaWebSocketPublisher;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VideoSessionServiceRoomReleaseTest {

    private final VideoSessionRepository repository = mock(VideoSessionRepository.class);
    private final VideoSourceRuntimeRepository runtimeRepository = mock(VideoSourceRuntimeRepository.class);
    private final MediaSessionViewerRepository viewerRepository = mock(MediaSessionViewerRepository.class);
    private final LiveKitRoomService roomService = mock(LiveKitRoomService.class);
    private final LiveKitTokenService tokenService = mock(LiveKitTokenService.class);
    private final MediaWebSocketPublisher publisher = mock(MediaWebSocketPublisher.class);
    private final FileService fileService = mock(FileService.class);
    private final MediaTrackService trackService = mock(MediaTrackService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final MediaProperties properties = new MediaProperties();
    private final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    private VideoSessionService service;
    private VideoSession target;
    private VideoSession shared;

    @BeforeEach
    void setUp() {
        properties.getSession().setIdleReleaseDelaySeconds(60);
        service = new VideoSessionService(
                repository,
                runtimeRepository,
                viewerRepository,
                roomService,
                tokenService,
                publisher,
                fileService,
                trackService,
                properties,
                transactionManager,
                entityManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        VideoSourceRuntime runtime = new VideoSourceRuntime();
        runtime.setRuntimeId("runtime-1");
        runtime.setRoomName("media.robot-1.camera01.visible.sub");
        when(runtimeRepository.findByIdForUpdate("runtime-1")).thenReturn(Optional.of(runtime));

        target = session("vs-target", VideoSessionStatus.IDLE_WAIT, now.minusMinutes(3));
        shared = session("vs-shared", VideoSessionStatus.IDLE_WAIT, now.minusMinutes(3));
        when(repository.findById("vs-target")).thenReturn(Optional.of(target));
        when(repository.findByIdForUpdate("vs-target")).thenReturn(Optional.of(target));
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-1"))
                .thenReturn(List.of(shared, target));
        when(repository
                .findByRuntimeIdIsNullAndSourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityOrderBySessionIdAsc(
                        VideoSourceType.ROBOT_CAMERA,
                        "robot-1",
                        "camera01",
                        VideoChannel.visible,
                        VideoQuality.sub))
                .thenReturn(List.of());
        when(viewerRepository.countBySessionIdAndLeftAtIsNull(any())).thenReturn(0L);
    }

    @Test
    void closesOnlyIdleSessionWhileAnotherSessionHasViewer() {
        shared.setStatus(VideoSessionStatus.STREAMING);
        when(viewerRepository.countBySessionIdAndLeftAtIsNull("vs-shared")).thenReturn(1L);

        Map<String, Object> result = service.releaseIdleSession("vs-target");

        assertThat(result).isEmpty();
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        assertThat(shared.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        verify(roomService, never()).deleteRoom(any());
        verify(trackService).unpublish(target);
    }

    @Test
    void keepsRoomForLegacySessionWithoutRuntimeId() {
        shared.setRuntimeId(null);
        shared.setStatus(VideoSessionStatus.STREAMING);
        when(repository.findByRuntimeIdOrderBySessionIdAsc("runtime-1")).thenReturn(List.of(target));
        when(repository
                .findByRuntimeIdIsNullAndSourceTypeAndSourceIdAndDeviceIdAndChannelAndQualityOrderBySessionIdAsc(
                        VideoSourceType.ROBOT_CAMERA,
                        "robot-1",
                        "camera01",
                        VideoChannel.visible,
                        VideoQuality.sub))
                .thenReturn(List.of(shared));
        when(viewerRepository.countBySessionIdAndLeftAtIsNull("vs-shared")).thenReturn(1L);

        assertThat(service.releaseIdleSession("vs-target")).isEmpty();

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        assertThat(shared.getStatus()).isEqualTo(VideoSessionStatus.STREAMING);
        verify(roomService, never()).deleteRoom(any());
    }

    @Test
    void keepsRoomWhileAnotherSessionHasIntercom() {
        shared.setStatus(VideoSessionStatus.ROOM_READY);
        shared.setIntercomStatus(IntercomStatus.ACTIVE);

        assertThat(service.releaseIdleSession("vs-target")).isEmpty();

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        verify(roomService, never()).deleteRoom(any());
    }

    @Test
    void keepsRoomWhileAnotherSessionHasRecording() {
        shared.setStatus(VideoSessionStatus.STREAMING);
        when(fileService.hasActiveLiveRecording("vs-shared")).thenReturn(true);

        assertThat(service.releaseIdleSession("vs-target")).isEmpty();

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        verify(roomService, never()).deleteRoom(any());
    }

    @Test
    void keepsRoomWhileAnotherSessionIsStarting() {
        shared.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
        shared.setCommandId("cmd-active");
        shared.setCommandRequestedAt(now);

        assertThat(service.releaseIdleSession("vs-target")).isEmpty();

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        verify(roomService, never()).deleteRoom(any());
    }

    @Test
    void waitsForNewestIdleDeadline() {
        shared.setIdleSince(now);

        assertThat(service.releaseIdleSession("vs-target")).isEmpty();

        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        assertThat(shared.getStatus()).isEqualTo(VideoSessionStatus.IDLE_WAIT);
        verify(roomService, never()).deleteRoom(any());
    }

    @Test
    void releasesRoomOnceAfterAllReferencesAndDeadlinesAreClear() {
        Map<String, Object> result = service.releaseIdleSession("vs-target");

        assertThat(result)
                .containsEntry("sessionId", "vs-target")
                .containsEntry("roomName", target.getRoomName())
                .containsEntry("sourceId", "robot-1");
        assertThat(target.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        assertThat(shared.getStatus()).isEqualTo(VideoSessionStatus.CLOSED);
        verify(trackService).unpublish(target);
        verify(trackService).unpublish(shared);
        verify(roomService).deleteRoom(target.getRoomName());
    }

    @Test
    void staleViewerCountDoesNotHideIdleReleaseCandidate() {
        target.setViewerCount(1);
        OffsetDateTime threshold = now.minusMinutes(1);
        when(repository.findByStatusAndIdleSinceBefore(VideoSessionStatus.IDLE_WAIT, threshold))
                .thenReturn(List.of(target));

        assertThat(service.idleReleaseCandidates(threshold)).containsExactly("vs-target");
    }

    private VideoSession session(String sessionId, VideoSessionStatus status, OffsetDateTime idleSince) {
        VideoSession session = new VideoSession();
        session.setSessionId(sessionId);
        session.setRuntimeId("runtime-1");
        session.setRobotId("robot-1");
        session.setSourceType(VideoSourceType.ROBOT_CAMERA);
        session.setSourceId("robot-1");
        session.setDeviceId("camera01");
        session.setChannel(VideoChannel.visible);
        session.setQuality(VideoQuality.sub);
        session.setRoomName("media.robot-1.camera01.visible.sub");
        session.setStatus(status);
        session.setViewerCount(0);
        session.setIntercomStatus(IntercomStatus.IDLE);
        session.setIdleSince(idleSince);
        session.setCreatedAt(now.minusMinutes(5));
        session.setUpdatedAt(idleSince);
        return session;
    }
}
