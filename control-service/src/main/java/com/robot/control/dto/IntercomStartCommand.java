package com.robot.control.dto;

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
public record IntercomStartCommand(
        String commandId,
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        String livekitUrl,
        String robotToken,
        boolean publishAudio,
        boolean subscribeOperatorAudio,
        boolean publishVideo,
        OffsetDateTime expiresAt) {
}
