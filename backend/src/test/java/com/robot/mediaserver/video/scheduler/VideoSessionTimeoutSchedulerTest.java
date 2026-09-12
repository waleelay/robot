package com.robot.mediaserver.video.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoSessionTimeoutSchedulerTest {

    @Test
    void scansPublishTimeoutByCommandRequestedAt() {
        VideoSessionRepository repository = mock(VideoSessionRepository.class);
        VideoSessionService service = mock(VideoSessionService.class);
        MediaProperties properties = new MediaProperties();
        properties.getSession().setTrackPublishTimeoutSeconds(20);
        VideoSession session = new VideoSession();
        session.setSessionId("vs-timeout");
        session.setCommandId("cmd-timeout");
        session.setStatus(VideoSessionStatus.REQUESTING_CLIENT);
        session.setCommandRequestedAt(OffsetDateTime.now().minusSeconds(30));
        session.setUpdatedAt(OffsetDateTime.now());
        when(repository.findByStatusAndCommandRequestedAtBefore(
                any(VideoSessionStatus.class), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0) == VideoSessionStatus.REQUESTING_CLIENT
                        ? List.of(session)
                        : List.of());
        when(repository.findByStatusAndSourceTypeAndCommandRequestedAtBefore(
                any(VideoSessionStatus.class), any(VideoSourceType.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        new VideoSessionTimeoutScheduler(repository, service, properties).sweep();

        verify(service).markTimeout(
                "vs-timeout", "cmd-timeout", "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时");
        verify(service).sweepStaleViewers();
        verify(service).sweepUnoccupiedSessions();
    }
}
