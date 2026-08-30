package com.robot.mediaserver.video.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
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
        videoSessionService.sweepStaleViewers();
    }

    private void handleTrackPublishTimeout() {
        OffsetDateTime currentTime = now();
        OffsetDateTime threshold = currentTime.minusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        List<VideoSession> requesting = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.REQUESTING_CLIENT, threshold);
        List<VideoSession> roomReady = repository.findByStatusAndUpdatedAtBefore(VideoSessionStatus.ROOM_READY, threshold);
        List<VideoSession> fixedCameraReady = repository.findByStatusAndSourceTypeAndUpdatedAtBefore(
                VideoSessionStatus.ROOM_READY, VideoSourceType.FIXED_CAMERA, currentTime);
        requesting.stream().filter(this::expectsVideoTrack)
                .forEach(session -> markTimeout(session, "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时"));
        roomReady.stream().filter(this::expectsVideoTrack)
                .filter(session -> session.getSourceType() != VideoSourceType.FIXED_CAMERA)
                .forEach(session -> markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时"));
        fixedCameraReady.stream().filter(this::expectsVideoTrack)
                .forEach(session -> {
                    if (!videoSessionService.confirmFixedCameraTrack(session.getSessionId())
                            && !session.getUpdatedAt().isAfter(threshold)) {
                        markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时");
                    }
                });
    }

    private boolean expectsVideoTrack(VideoSession session) {
        return !session.isIntercomAudioOnly();
    }

    private void markTimeout(VideoSession session, String errorCode, String message) {
        try {
            videoSessionService.markTimeout(session.getSessionId(), errorCode, message);
        } catch (Exception ex) {
            log.warn("标记视频会话超时失败 session={}", session.getSessionId(), ex);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
