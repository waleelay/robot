package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 对讲会话响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param sessionId 会话 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param roomName LiveKit 房间名
 * @param videoStatus 视频状态
 * @param intercomStatus 对讲状态
 * @param intercomAudioOnly 是否仅对讲音频
 * @param livekitUrl LiveKit 地址
 * @param operatorToken 操作端 Token
 * @param expiresAt 过期时间
 */
@Schema(name = "IntercomResponse", description = "对讲会话响应。")
public record IntercomResponse(
        @Schema(
                description = "视频会话 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sessionId,
        @Schema(
                description = "机器人 ID；固定摄像头场景按现有来源映射填写",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String robotId,
        @Schema(
                description = "相机或设备组件 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String deviceId,
        @Schema(
                description = "LiveKit 房间名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String roomName,
        @Schema(
                description = "视频会话状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoSessionStatus videoStatus,
        @Schema(
                description = "对讲状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        IntercomStatus intercomStatus,
        @Schema(description = "是否仅对讲音频", requiredMode = Schema.RequiredMode.REQUIRED) boolean intercomAudioOnly,
        @Schema(
                description = "LiveKit 连接地址",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String livekitUrl,
        @Schema(
                description = "当前对讲操作端令牌",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String operatorToken,
        @Schema(
                description = "有效期截止时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime expiresAt) {
}
