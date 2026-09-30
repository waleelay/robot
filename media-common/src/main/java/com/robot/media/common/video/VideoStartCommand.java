package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 下发给机器人客户端的视频启动命令。
 * @author leelay
 * @date 2026-07-05
 * @param commandId 命令 ID
 * @param sessionId 会话 ID
 * @param robotId 机器人 ID
 * @param deviceId 设备 ID
 * @param channel 视频通道
 * @param quality 视频清晰度
 * @param livekitUrl LiveKit 地址
 * @param roomName LiveKit 房间名
 * @param publisherToken 发布端 Token
 * @param publishIdentity 发布身份
 * @param rtspUrl 外部媒体源 RTSP 地址，机器人摄像头为空
 * @param expiresAt 过期时间
 * @param sourceType 视频来源类型
 * @param sourceId 归一化后的视频源标识；机器人来源对应机器人，固定摄像头来源对应摄像头
 */
@Schema(name = "VideoStartCommand", description = "下发给机器人客户端的视频启动命令。")
public record VideoStartCommand(
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
                description = "视频来源类型",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        VideoSourceType sourceType,
        @Schema(
                description = "视频源 ID；创建请求缺省时使用 robotId",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String sourceId,
        @Schema(
                description = "相机或设备组件 ID",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String deviceId,
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
                description = "LiveKit 连接地址",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String livekitUrl,
        @Schema(
                description = "LiveKit 房间名",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String roomName,
        @Schema(
                description = "发布令牌，仅供目标发布者使用",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String publisherToken,
        @Schema(
                description = "预期媒体发布者身份",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String publishIdentity,
        @Schema(
                description = "可信发布者使用的 RTSP 地址，可能含凭据，不得写入日志",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String rtspUrl,
        @Schema(
                description = "有效期截止时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                types = {"string", "null"})
        OffsetDateTime expiresAt) {
}
