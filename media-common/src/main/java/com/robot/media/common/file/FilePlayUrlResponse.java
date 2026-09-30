package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 文件播放地址响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param fileId 文件 ID
 * @param format 播放格式
 * @param contentType 内容类型
 * @param playUrl 播放地址
 * @param expiresAt 过期时间
 */
public record FilePlayUrlResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(description = "播放格式，当前为 hls", requiredMode = Schema.RequiredMode.REQUIRED)
        String format,
        @Schema(description = "文件媒体类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,
        @Schema(description = "带播放 token 的 HLS 地址；Control 会改写为控制侧路径", requiredMode = Schema.RequiredMode.REQUIRED)
        String playUrl,
        @Schema(
                description = "本次返回的有效期，上海时区",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime expiresAt) {
}
