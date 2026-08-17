package com.robot.media.common.video;

import java.time.OffsetDateTime;

/**
 * 下发给机器人客户端的视频启动命令。
 *
 * @author leelay
 * @date 2026-07-05
 *
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
 */
public record VideoStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        VideoSourceType sourceType,
        String sourceId,
        String deviceId,
        VideoChannel channel,
        VideoQuality quality,
        String livekitUrl,
        String roomName,
        String publisherToken,
        String publishIdentity,
        String rtspUrl,
        OffsetDateTime expiresAt) {
}
