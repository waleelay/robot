package com.robot.mediaserver.video.dto;

import java.time.OffsetDateTime;

/**
 * 前端观看 Token 响应。
 *
 * @author leelay
 * @date 2026/05/19
 */
public record ViewerTokenResponse(String livekitUrl, String roomName, String token, OffsetDateTime expiresAt) {
}
