package com.robot.control.dto;

import java.time.OffsetDateTime;

public record ViewerTokenResponse(String livekitUrl, String roomName, String token, OffsetDateTime expiresAt) {
}
