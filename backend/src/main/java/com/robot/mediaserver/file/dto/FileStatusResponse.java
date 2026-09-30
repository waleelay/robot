package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 机器人查询的文件处理状态；uploadedAt 和错误信息在对应阶段尚未产生时可为空。
 *
 * @param fileId 文件 ID
 * @param status 文件处理状态
 * @param fileSize 文件字节数
 * @param ready 文件及必要后处理是否已就绪
 * @param errorCode 错误码；无错误时为空
 * @param errorMessage 失败原因；无错误时为空
 * @param uploadedAt 上传完成时间；未完成时为空
 */
public record FileStatusResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(description = "文件处理状态", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(description = "文件字节数", requiredMode = Schema.RequiredMode.REQUIRED)
        long fileSize,
        @Schema(description = "文件及必要后处理是否已就绪", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ready,
        @Schema(description = "错误码；无错误时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String errorCode,
        @Schema(description = "失败原因；无错误时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String errorMessage,
        @Schema(
                description = "上传完成时间；未完成时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime uploadedAt) {
}
