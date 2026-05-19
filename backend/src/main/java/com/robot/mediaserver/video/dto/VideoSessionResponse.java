package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.VideoChannel;
import com.robot.mediaserver.video.model.VideoQuality;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.mediaserver.video.model.VideoSessionStatus;
import java.time.OffsetDateTime;

/**
 * 实时视频会话响应。
 *
 * @author leelay
 * @date 2026/05/19
 */
public record VideoSessionResponse(
        String sessionId,
        String robotId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        VideoSessionStatus status,
        String roomName,
        String livekitUrl,
        String viewerToken,
        String trackSid,
        String trackName,
        int viewerCount,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * 从会话实体构建响应对象。
     *
     * @param session 会话实体
     * @param livekitUrl LiveKit 访问地址
     * @param viewerToken 前端观看 Token
     * @return 会话响应
     */
    public static VideoSessionResponse from(VideoSession session, String livekitUrl, String viewerToken) {
        return new VideoSessionResponse(
                session.getSessionId(),
                session.getRobotId(),
                session.getDeviceId(),
                session.getChannel(),
                session.getQuality(),
                session.getStatus(),
                session.getRoomName(),
                livekitUrl,
                viewerToken,
                session.getTrackSid(),
                session.getTrackName(),
                session.getViewerCount(),
                session.getLastErrorCode(),
                session.getLastErrorMessage(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
