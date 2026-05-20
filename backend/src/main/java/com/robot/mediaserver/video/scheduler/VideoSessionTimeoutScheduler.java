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

/**
 * 实时视频会话超时扫描器。
 *
 * <p>负责处理客户端 ACK 超时、Track 发布超时、断流恢复超时和无人观看延迟释放。</p>
 *
 * @author leelay
 * @date 2026/05/20
 */
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

    /**
     * 扫描需要自动推进的实时视频会话。
     */
    @Scheduled(fixedDelayString = "${media.session.sweep-delay-ms:5000}")
    public void sweep() {
        handleClientAckTimeout();
        handleTrackPublishTimeout();
        handleInterruptedTimeout();
        handleIdleRelease();
    }

    private void handleClientAckTimeout() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getClientAckTimeoutSeconds());
        List<VideoSession> sessions = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.REQUESTING_CLIENT, threshold);
        for (VideoSession session : sessions) {
            markTimeout(session, "CLIENT_ACK_TIMEOUT", "云接入客户端 ACK 超时");
        }
    }

    private void handleTrackPublishTimeout() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        List<VideoSession> clientAcked = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.CLIENT_ACKED, threshold);
        List<VideoSession> roomReady = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.ROOM_READY, threshold);
        clientAcked.forEach(session -> markTimeout(session, "LK_PUBLISH_TIMEOUT", "客户端 ACK 后 Track 发布超时"));
        roomReady.forEach(session -> markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时"));
    }

    private void handleInterruptedTimeout() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getInterruptedGraceSeconds());
        List<VideoSession> sessions = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.INTERRUPTED, threshold);
        for (VideoSession session : sessions) {
            markTimeout(session, "TRACK_INTERRUPTED_TIMEOUT", "Track 中断后恢复超时");
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
