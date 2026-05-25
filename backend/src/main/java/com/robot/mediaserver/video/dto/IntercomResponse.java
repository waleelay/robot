package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.IntercomStatus;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import java.time.OffsetDateTime;

/**
 * 绑定视频会话的对讲启动或 Token 响应。
 */
public record IntercomResponse(
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        VideoSessionStatus videoStatus,
        IntercomStatus intercomStatus,
        boolean intercomAudioOnly,
        String livekitUrl,
        String operatorToken,
        OffsetDateTime expiresAt) {

    public static IntercomResponse from(
            VideoSession session,
            String livekitUrl,
            String operatorToken,
            OffsetDateTime expiresAt) {
        return new IntercomResponse(
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getRoomName(),
                session.getStatus(),
                session.getIntercomStatus(),
                session.isIntercomAudioOnly(),
                livekitUrl,
                operatorToken,
                expiresAt);
    }
}
