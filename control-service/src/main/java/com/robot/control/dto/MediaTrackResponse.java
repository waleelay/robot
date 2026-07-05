package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 媒体 Track 响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param trackId Track ID
 * @param sessionId 会话 ID
 * @param trackSid Track SID
 * @param trackName Track 名称
 * @param participantIdentity 参与者身份
 * @param kind Track 类型
 * @param channel 视频通道
 * @param quality 视频清晰度
 * @param publishedAt 发布时间
 * @param unpublishedAt 取消发布时间
 */
public record MediaTrackResponse(
        String trackId,
        String sessionId,
        String trackSid,
        String trackName,
        String participantIdentity,
        String kind,
        VideoChannel channel,
        VideoQuality quality,
        OffsetDateTime publishedAt,
        OffsetDateTime unpublishedAt) {
}
