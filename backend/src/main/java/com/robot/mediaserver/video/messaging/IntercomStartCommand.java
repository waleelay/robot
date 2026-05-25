package com.robot.mediaserver.video.messaging;

import java.time.OffsetDateTime;

/**
 * 在既有视频 Room 内启动机器人双向音频的 MQTT 指令。
 */
public record IntercomStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        String livekitUrl,
        String robotToken,
        boolean publishAudio,
        boolean subscribeOperatorAudio,
        boolean publishVideo,
        OffsetDateTime expiresAt) {
}
