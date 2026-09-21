package com.robot.control.fixedcamera;

import com.robot.control.client.ControlMediaServiceClient;
import com.robot.control.messaging.RobotMediaCommandService;
import com.robot.media.common.video.FixedCameraPublisherModeResponse;
import com.robot.media.common.video.FixedCameraPublisherPresenceResponse;
import com.robot.media.common.video.VideoPublisherMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 编排 Media 发布模式与固定摄像头 Gateway 停止命令。 */
@Service
public class FixedCameraPublisherLifecycleService {

    private final ControlMediaServiceClient mediaServiceClient;
    private final RobotMediaCommandService commandService;

    @Value("${control.fixed-camera-publisher.presence-timeout-seconds:5}")
    private long presenceTimeoutSeconds = 5L;

    @Value("${control.fixed-camera-publisher.presence-poll-millis:200}")
    private long presencePollMillis = 200L;

    public FixedCameraPublisherLifecycleService(
            ControlMediaServiceClient mediaServiceClient,
            RobotMediaCommandService commandService) {
        this.mediaServiceClient = mediaServiceClient;
        this.commandService = commandService;
    }

    public FixedCameraPublisherModeResponse switchMode(
            String cameraId,
            VideoPublisherMode targetMode,
            long publisherRevision) {
        validateTargetMode(targetMode);
        FixedCameraPublisherModeResponse response = mediaServiceClient.switchFixedCameraPublisherMode(
                cameraId, targetMode, publisherRevision);
        sendStops(response);
        if (targetMode == VideoPublisherMode.LIVEKIT_INGRESS
                && (response == null || response.stopCommands() == null || response.stopCommands().isEmpty())) {
            return response;
        }
        awaitPublisherExit(cameraId);
        return response;
    }

    public FixedCameraPublisherModeResponse quiesceForDelete(String cameraId, long publisherRevision) {
        FixedCameraPublisherModeResponse response = mediaServiceClient.quiesceFixedCameraPublisher(
                cameraId, publisherRevision);
        sendStops(response);
        awaitPublisherExit(cameraId);
        return response;
    }

    private void sendStops(FixedCameraPublisherModeResponse response) {
        if (response == null || response.stopCommands() == null) {
            return;
        }
        response.stopCommands().forEach(commandService::sendFixedCameraStop);
    }

    private void awaitPublisherExit(String cameraId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(Math.max(1L, presenceTimeoutSeconds)));
        do {
            FixedCameraPublisherPresenceResponse presence = mediaServiceClient.fixedCameraPublisherPresence(cameraId);
            if (presence != null && !presence.participantPresent() && !presence.trackPresent()) {
                return;
            }
            sleep();
        } while (Instant.now().isBefore(deadline));
        throw new IllegalStateException("固定摄像头旧发布端未在时限内退出：" + cameraId);
    }

    private void sleep() {
        try {
            Thread.sleep(Math.max(50L, Math.min(presencePollMillis, 1000L)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待固定摄像头发布端退出时被中断", exception);
        }
    }

    private void validateTargetMode(VideoPublisherMode targetMode) {
        if (targetMode != VideoPublisherMode.FIXED_CAMERA_GATEWAY
                && targetMode != VideoPublisherMode.LIVEKIT_INGRESS) {
            throw new IllegalArgumentException("固定摄像头目标发布模式不合法");
        }
    }
}
