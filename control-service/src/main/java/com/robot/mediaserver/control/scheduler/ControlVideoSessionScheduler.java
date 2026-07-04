package com.robot.mediaserver.control.scheduler;

import com.robot.mediaserver.control.config.ControlServiceProperties;
import com.robot.mediaserver.control.client.ControlMediaServiceClient;
import com.robot.mediaserver.control.service.ControlVideoCommandService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ControlVideoSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ControlVideoSessionScheduler.class);

    private final ControlMediaServiceClient mediaServiceClient;
    private final ControlVideoCommandService commandService;
    private final ControlServiceProperties properties;

    public ControlVideoSessionScheduler(
            ControlMediaServiceClient mediaServiceClient,
            ControlVideoCommandService commandService,
            ControlServiceProperties properties) {
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${media.session.sweep-delay-ms:5000}")
    public void sweep() {
        restartInterruptedSessions();
        expireStaleIntercoms();
        releaseIdleSessions();
    }

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

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
