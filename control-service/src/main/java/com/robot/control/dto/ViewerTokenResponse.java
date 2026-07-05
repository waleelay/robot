package com.robot.control.dto;

import java.time.OffsetDateTime;

/**
 * 观看者 LiveKit Token 响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param livekitUrl LiveKit 地址
 * @param roomName LiveKit 房间名
 * @param token Token
 * @param expiresAt 过期时间
 */
public record ViewerTokenResponse(String livekitUrl, String roomName, String token, OffsetDateTime expiresAt) {
}
