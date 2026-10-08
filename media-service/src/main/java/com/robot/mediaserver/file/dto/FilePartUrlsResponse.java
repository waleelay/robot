package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 本次签发的分片地址及过期时间；时间按服务统一的上海时区格式序列化。
 *
 * @param expiresAt 本次返回的有效期，上海时区
 * @param parts 本次签发的分片地址
 */
public record FilePartUrlsResponse(
        @Schema(
                description = "本次返回的有效期，上海时区",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime expiresAt,
        @Schema(description = "本次签发的分片地址", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FilePartUploadUrlResponse> parts) {
}
