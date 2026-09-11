package com.robot.media.common.video;

import java.time.OffsetDateTime;

/**
 * 现场 App 视频呼叫会话响应：双方 LiveKit Token。
 *
 * @param callId 呼叫 ID
 * @param roomName LiveKit 房间名
 * @param livekitUrl 客户端可达的 LiveKit 地址
 * @param appToken 现场 App Token（可发布摄像头与麦克风）
 * @param centerToken 中心端 Token（可订阅并发布麦克风）
 * @param expiresAt Token 过期时间
 */
public record FieldCallResponse(
        String callId,
        String roomName,
        String livekitUrl,
        String appToken,
        String centerToken,
        OffsetDateTime expiresAt) {
}
