package com.robot.mediaserver.video.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VideoSessionTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionTimeoutScheduler.class);

    private final VideoSessionRepository repository;
    private final VideoSessionService videoSessionService;
    private final MediaProperties properties;

    public VideoSessionTimeoutScheduler(
            VideoSessionRepository repository,
            VideoSessionService videoSessionService,
            MediaProperties properties) {
        this.repository = repository;
        this.videoSessionService = videoSessionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${media.session.sweep-delay-ms:5000}")
    public void sweep() {
        handleTrackPublishTimeout();
        handleInterruptedTimeout();
        handleIdleRelease();
    }

    private void handleTrackPublishTimeout() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        List<VideoSession> requesting = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.REQUESTING_CLIENT, threshold);
        List<VideoSession> roomReady = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.ROOM_READY, threshold);
        requesting.forEach(session -> markTimeout(session, "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时"));
        roomReady.forEach(session -> markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时"));
    }

    private void handleInterruptedTimeout() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getInterruptedGraceSeconds());
        List<VideoSession> sessions = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.INTERRUPTED, threshold);
        for (VideoSession session : sessions) {
            if (session.getViewerCount() > 0) {
                videoSessionService.restartSession(session.getSessionId());
            } else {
                markTimeout(session, "TRACK_INTERRUPTED_TIMEOUT", "Track 中断后恢复超时");
            }
        }
    }

    private void handleIdleRelease() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getIdleReleaseDelaySeconds());
        List<VideoSession> sessions = repository.findByStatusAndIdleSinceBefore(VideoSessionStatus.IDLE_WAIT, threshold);
        for (VideoSession session : sessions) {
            try {
                videoSessionService.releaseIdleSession(session.getSessionId());
            } catch (Exception ex) {
                log.warn("Failed to release idle session={}", session.getSessionId(), ex);
            }
        }
    }

    private void markTimeout(VideoSession session, String errorCode, String message) {
        try {
            videoSessionService.markTimeout(session.getSessionId(), errorCode, message);
        } catch (Exception ex) {
            log.warn("Failed to mark session timeout session={}", session.getSessionId(), ex);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
