package com.robot.control.scheduler;

import com.robot.control.config.ControlServiceProperties;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.service.ControlVideoCommandService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 视频会话恢复、释放与对讲超时调度任务。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class ControlVideoSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ControlVideoSessionScheduler.class);

    private final ControlMediaServiceClient mediaServiceClient;
    private final ControlVideoCommandService commandService;
    private final ControlServiceProperties properties;

    /**
     * 创建 ControlVideoSessionScheduler 实例。
     *
     * @param mediaServiceClient Media Service 客户端
     * @param commandService 视频命令服务
     * @param properties 服务配置
     */
    public ControlVideoSessionScheduler(
            ControlMediaServiceClient mediaServiceClient,
            ControlVideoCommandService commandService,
            ControlServiceProperties properties) {
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.properties = properties;
    }

    /**
     * 执行周期扫描任务。
     */
    @Scheduled(fixedDelayString = "${media.session.sweep-delay-ms:5000}")
    public void sweep() {
        restartInterruptedSessions();
        expireStaleIntercoms();
        releaseIdleSessions();
    }

    /**
     * 处理过期对讲会话。
     */
    private void expireStaleIntercoms() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getViewerHeartbeatTimeoutSeconds());
        try {
            mediaServiceClient.intercomTimeoutCandidates(threshold).forEach(sessionId -> {
                try {
                    commandService.expireIntercom(sessionId);
                } catch (Exception ex) {
                    log.warn("Failed to expire intercom session={}", sessionId, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to query stale intercom sessions", ex);
        }
    }

    /**
     * 恢复中断的视频会话。
     */
    private void restartInterruptedSessions() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getInterruptedGraceSeconds());
        try {
            mediaServiceClient.interruptedRestartCandidates(threshold).forEach(sessionId -> {
                try {
                    commandService.restartSession(sessionId);
                } catch (Exception ex) {
                    log.warn("Failed to restart interrupted session={}", sessionId, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to query interrupted sessions", ex);
        }
    }

    /**
     * 释放空闲视频会话。
     */
    private void releaseIdleSessions() {
        OffsetDateTime threshold = now().minusSeconds(properties.getSession().getIdleReleaseDelaySeconds());
        try {
            mediaServiceClient.idleReleaseCandidates(threshold).forEach(sessionId -> {
                try {
                    commandService.releaseIdleSession(sessionId);
                } catch (Exception ex) {
                    log.warn("Failed to release idle session={}", sessionId, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to query idle sessions", ex);
        }
    }

    /**
     * 返回当前时间。
     *
     * @return 当前时间
     */
    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
