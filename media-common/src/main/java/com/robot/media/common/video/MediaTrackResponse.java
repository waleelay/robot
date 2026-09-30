package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 媒体 Track 响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param trackId 媒体轨道记录标识
 * @param sessionId 会话 ID
 * @param trackSid 媒体轨道标识
 * @param trackName Track 名称
 * @param participantIdentity 参与者身份
 * @param kind Track 类型
 * @param channel 视频通道
 * @param quality 视频清晰度
 * @param publishedAt 发布时间
 * @param unpublishedAt 取消发布时间
 */
@Schema(name = "MediaTrackResponse", description = "媒体 Track 响应。")
public record MediaTrackResponse(
        @Schema(
                description = "平台媒体轨道记录 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String trackId,
        @Schema(
                description = "视频会话 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sessionId,
        @Schema(
                description = "LiveKit 轨道标识",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String trackSid,
        @Schema(
                description = "媒体轨道名称",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String trackName,
        @Schema(
                description = "LiveKit 参与者身份",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String participantIdentity,
        @Schema(
                description = "媒体类型",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String kind,
        @Schema(
                description = "视频通道",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoChannel channel,
        @Schema(
                description = "视频清晰度；缺省取该入口的 DTO 默认值",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoQuality quality,
        @Schema(
                description = "轨道发布时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime publishedAt,
        @Schema(
                description = "轨道取消发布时间，尚在发布时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime unpublishedAt) {
}
