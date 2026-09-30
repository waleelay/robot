package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 文件下载地址响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param fileId 文件 ID
 * @param downloadUrl 下载地址
 * @param expiresAt 过期时间
 */
public record FileDownloadUrlResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(description = "限时下载地址；inline 请求决定展示方式，不记录完整签名地址", requiredMode = Schema.RequiredMode.REQUIRED)
        String downloadUrl,
        @Schema(
                description = "本次返回的有效期，上海时区",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime expiresAt) {
}
