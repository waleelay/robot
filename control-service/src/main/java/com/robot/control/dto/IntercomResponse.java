package com.robot.control.dto;

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
public record IntercomResponse(
        String sessionId,
        String robotId,
        String deviceId,
        String roomName,
        VideoSessionStatus videoStatus,
        IntercomStatus intercomStatus,
        boolean intercomAudioOnly,
        String livekitUrl,
        String operatorToken,
        OffsetDateTime expiresAt) {
}
