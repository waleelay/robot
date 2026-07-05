package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 视频会话响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param sessionId 会话 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param channel 视频通道
 * @param quality 视频清晰度
 * @param status 状态
 * @param roomName LiveKit 房间名
 * @param livekitUrl LiveKit 地址
 * @param viewerToken 观看 Token
 * @param trackSid Track SID
 * @param trackName Track 名称
 * @param viewerCount 观看人数
 * @param intercomStatus 对讲状态
 * @param intercomAudioOnly 是否仅对讲音频
 * @param intercomOperatorId 对讲操作员 ID
 * @param robotAudioTrackSid 机器人音频 Track SID
 * @param robotAudioTrackName 机器人音频 Track 名称
 * @param lastErrorCode 最后错误码
 * @param lastErrorMessage 最后错误消息
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
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
        IntercomStatus intercomStatus,
        boolean intercomAudioOnly,
        String intercomOperatorId,
        String robotAudioTrackSid,
        String robotAudioTrackName,
        String lastErrorCode,
        String lastErrorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
