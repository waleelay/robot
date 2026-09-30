package com.robot.mediaserver.video.dto;

import com.robot.mediaserver.video.model.MediaTrack;
import com.robot.mediaserver.video.model.VideoSession;
import com.robot.media.common.video.IntercomResponse;
import com.robot.media.common.video.MediaTrackResponse;
import com.robot.media.common.video.VideoSessionResponse;
import com.robot.media.common.video.VideoPublisherMode;
import java.time.OffsetDateTime;

/**
 * 共享视频 DTO 与 JPA 实体之间的转换工厂。
 *
 * <p>共享契约模块 media-common 只包含纯 DTO 与枚举，不依赖 JPA 实体；
 * 本类在媒体服务侧补充从 {@link VideoSession}、{@link MediaTrack}
 * 构建响应对象的方法。</p>
 */
public final class VideoSessionResponses {

    private VideoSessionResponses() {
    }

    /**
     * 从视频会话实体构建会话响应。
     *
     * @param session 视频会话实体
     * @param livekitUrl LiveKit 地址
     * @param viewerToken 观看 Token，可为空
     * @return 会话响应
     */
    public static VideoSessionResponse from(VideoSession session, String livekitUrl, String viewerToken) {
        return from(session, livekitUrl, viewerToken, null, 0);
    }

    /**
     * 将媒体会话持久化状态转换为共享响应，保留缺测值及当前调用方的观看令牌。
     * @param session 业务视频会话实体
     * @param livekitUrl LiveKit 地址
     * @param viewerToken 观看 Token
     * @param publisherMode 当前发布模式
     * @param publisherRevision 发布模式版本，用于拒绝陈旧发布者操作
     * @return 可交给 Control 或浏览器消费的会话响应
     */
    public static VideoSessionResponse from(
            VideoSession session,
            String livekitUrl,
            String viewerToken,
            VideoPublisherMode publisherMode,
            long publisherRevision) {
        return new VideoSessionResponse(
                session.getSessionId(),
                session.getRobotId(),
                session.getSourceType(),
                session.getSourceId(),
                publisherMode,
                publisherRevision,
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
                session.getIntercomStatus(),
                session.isIntercomAudioOnly(),
                session.getIntercomOperatorId(),
                session.getIntercomClientId(),
                session.getRobotAudioTrackSid(),
                session.getRobotAudioTrackName(),
                session.getLastErrorCode(),
                session.getLastErrorMessage(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    /**
     * 从视频会话实体构建对讲响应。
     *
     * @param session 视频会话实体
     * @param livekitUrl LiveKit 地址
     * @param operatorToken 操作员 Token
     * @param expiresAt Token 过期时间
     * @return 对讲响应
     */
    public static IntercomResponse intercom(
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

    /**
     * 从媒体 Track 实体构建 Track 响应。
     *
     * @param track 媒体 Track 实体
     * @return Track 响应
     */
    public static MediaTrackResponse from(MediaTrack track) {
        return new MediaTrackResponse(
                track.getTrackId(),
                track.getSessionId(),
                track.getTrackSid(),
                track.getTrackName(),
                track.getParticipantIdentity(),
                track.getKind(),
                track.getChannel(),
                track.getQuality(),
                track.getPublishedAt(),
                track.getUnpublishedAt());
    }
}
