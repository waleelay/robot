package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 下发给机器人客户端的对讲启动命令。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param commandId 命令 ID
 * @param sessionId 会话 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param roomName LiveKit 房间名
 * @param livekitUrl LiveKit 地址
 * @param robotToken 机器人端 Token
 * @param publishAudio 是否发布音频
 * @param subscribeOperatorAudio 是否订阅操作端音频
 * @param publishVideo 是否发布视频
 * @param expiresAt 过期时间
 */
@Schema(name = "IntercomStartCommand", description = "下发给机器人客户端的对讲启动命令。")
public record IntercomStartCommand(
        @Schema(
                description = "指令 ID，用于关联客户端状态",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String commandId,
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
                description = "LiveKit 连接地址",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String livekitUrl,
        @Schema(
                description = "机器人音频发布及订阅令牌",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String robotToken,
        @Schema(description = "是否要求发布音频", requiredMode = Schema.RequiredMode.REQUIRED) boolean publishAudio,
        @Schema(
                description = "是否要求订阅操作员音频",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean subscribeOperatorAudio,
        @Schema(description = "是否同时要求发布视频", requiredMode = Schema.RequiredMode.REQUIRED) boolean publishVideo,
        @Schema(
                description = "有效期截止时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime expiresAt) {
}
