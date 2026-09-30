package com.robot.mediaserver.video.scheduler;

import com.robot.mediaserver.config.MediaProperties;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.VideoSessionStatus;
import com.robot.media.common.video.VideoSourceType;
import com.robot.mediaserver.video.repository.VideoSessionRepository;
import com.robot.mediaserver.video.service.VideoSchedulerLeaseService;
import com.robot.mediaserver.video.service.FixedCameraIngressService;
import com.robot.mediaserver.video.service.VideoSessionService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 在调度租约保护下检查发布超时、观看端失联及会话释放。 */
@Component
public class VideoSessionTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(VideoSessionTimeoutScheduler.class);
    private static final String SCHEDULER_LEASE_NAME = "media-video-session-maintenance";

    private final VideoSessionRepository repository;
    private final VideoSessionService videoSessionService;
    private final FixedCameraIngressService fixedCameraIngressService;
    private final VideoSchedulerLeaseService schedulerLeaseService;
    private final MediaProperties properties;

    /**
     * 初始化 VideoSessionTimeoutScheduler，保存所需依赖及初始运行状态。
     *
     * @param repository 实时视频会话仓储。
     * @param videoSessionService 实时视频会话编排服务。
     * @param fixedCameraIngressService 固定摄像头 RTMP Ingress 配置生命周期。
     * @param schedulerLeaseService 基于数据库短租约保证视频周期任务在多 Media 实例中只有一个执行者。
     * @param properties 服务配置
     */
    public VideoSessionTimeoutScheduler(
            VideoSessionRepository repository,
            VideoSessionService videoSessionService,
            FixedCameraIngressService fixedCameraIngressService,
            VideoSchedulerLeaseService schedulerLeaseService,
            MediaProperties properties) {
        this.repository = repository;
        this.videoSessionService = videoSessionService;
        this.fixedCameraIngressService = fixedCameraIngressService;
        this.schedulerLeaseService = schedulerLeaseService;
        this.properties = properties;
    }

    /**
     * 取得跨实例调度租约后检查视频发布超时、过期观看者及无占用会话。
     */
    @Scheduled(fixedDelayString = "${media.session.sweep-delay-ms:5000}")
    public void sweep() {
        schedulerLeaseService.execute(SCHEDULER_LEASE_NAME, () -> {
            handleTrackPublishTimeout();
            videoSessionService.sweepStaleViewers();
            videoSessionService.sweepUnoccupiedSessions();
        });
    }

    /**
     * 定期核验 LiveKit 发布者和轨道事实，纠正漏回调或断连后的会话状态。
     */
    @Scheduled(fixedDelayString = "${media.livekit.reconcile-delay-ms:5000}")
    public void reconcileLiveKitTracks() {
        schedulerLeaseService.execute(SCHEDULER_LEASE_NAME, () -> {
            fixedCameraIngressService.reconcileManagedResources();
            videoSessionService.reconcileLiveKitTracks();
        });
    }

    private void handleTrackPublishTimeout() {
        OffsetDateTime currentTime = now();
        OffsetDateTime threshold = currentTime.minusSeconds(properties.getSession().getTrackPublishTimeoutSeconds());
        List<VideoSession> requesting = repository.findByStatusAndCommandRequestedAtBefore(
                VideoSessionStatus.REQUESTING_CLIENT, threshold);
        List<VideoSession> roomReady = repository.findByStatusAndCommandRequestedAtBefore(
                VideoSessionStatus.ROOM_READY, threshold);
        List<VideoSession> fixedCameraReady = repository.findByStatusAndSourceTypeAndCommandRequestedAtBefore(
                VideoSessionStatus.ROOM_READY, VideoSourceType.FIXED_CAMERA, threshold);
        requesting.stream().filter(this::expectsVideoTrack)
                .forEach(session -> markTimeout(session, "CLIENT_PUBLISH_TIMEOUT", "客户端发布超时"));
        roomReady.stream().filter(this::expectsVideoTrack)
                .filter(session -> session.getSourceType() != VideoSourceType.FIXED_CAMERA)
                .forEach(session -> markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时"));
        fixedCameraReady.stream().filter(this::expectsVideoTrack)
                .forEach(session -> {
                    if (!videoSessionService.confirmFixedCameraTrack(session.getSessionId())) {
                        markTimeout(session, "LK_PUBLISH_TIMEOUT", "Room ready 后 Track 发布超时");
                    }
                });
    }

    private boolean expectsVideoTrack(VideoSession session) {
        return !session.isIntercomAudioOnly();
    }

    private void markTimeout(VideoSession session, String errorCode, String message) {
        try {
            videoSessionService.markTimeout(
                    session.getSessionId(), session.getCommandId(), errorCode, message);
        } catch (Exception ex) {
            log.warn("标记视频会话超时失败 会话标识={}", session.getSessionId(), ex);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
