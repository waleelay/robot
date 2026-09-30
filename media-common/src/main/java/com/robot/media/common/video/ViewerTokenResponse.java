package com.robot.media.common.video;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 观看者 LiveKit Token 响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param livekitUrl LiveKit 地址
 * @param roomName LiveKit 房间名
 * @param token 观看端访问令牌
 * @param expiresAt 过期时间
 */
@Schema(name = "ViewerTokenResponse", description = "观看者 LiveKit Token 响应。")
public record ViewerTokenResponse(@Schema(description = "LiveKit 连接地址", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String livekitUrl, @Schema(description = "LiveKit 房间名", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String roomName, @Schema(description = "当前调用方的媒体访问令牌", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"}) String token, @Schema(description = "有效期截止时间", requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class, pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", types = {"string", "null"}) OffsetDateTime expiresAt) {
}
