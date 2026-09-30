package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 现场应用 视频呼叫会话响应：双方 LiveKit Token。
 *
 * @param callId 呼叫 ID
 * @param roomName LiveKit 房间名
 * @param livekitUrl 客户端可达的 LiveKit 地址
 * @param appToken 现场应用 Token（可发布摄像头与麦克风）
 * @param centerToken 中心端 Token（可订阅并发布麦克风）
 * @param expiresAt Token 过期时间
 */
@Schema(name = "FieldCallResponse", description = "现场 App 视频呼叫会话响应：双方 LiveKit Token。")
public record FieldCallResponse(
        @Schema(
                description = "呼叫 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String callId,
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
                description = "现场 App 的房间令牌",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String appToken,
        @Schema(
                description = "指挥中心的房间令牌",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String centerToken,
        @Schema(
                description = "有效期截止时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime expiresAt) {
}
