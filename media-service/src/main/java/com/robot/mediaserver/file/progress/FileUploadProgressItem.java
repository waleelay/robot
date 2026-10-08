package com.robot.mediaserver.file.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 文件进度快照；上传字节和处理阶段分开表达，上传百分比达到 100 不代表文件已可用。
 * @param fileId 文件 ID
 * @param uploadId 最近一次上传会话 ID；没有上传会话记录时为空
 * @param fileName 原始文件名
 * @param fileType 文件业务类型
 * @param phase 上传及后处理的统一展示阶段
 * @param uploadedBytes 已确认上传的字节数
 * @param totalBytes 预期文件总字节数
 * @param uploadedPartCount 已确认上传分片数
 * @param partCount 分片总数；没有上传会话时为空
 * @param uploadPercent 上传百分比，100 不等于后处理已完成
 * @param ready 文件及必要后处理是否已就绪
 * @param version 进度版本；用于识别进度更新，终态快照可为 0
 * @param updatedAt 进度更新时间
 * @param errorCode 错误码；无错误时为空
 * @param errorMessage 失败原因；无错误时为空
 */
public record FileUploadProgressItem(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(
                description = "平台上传会话 ID；来源文件已完成时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String uploadId,
        @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileName,
        @Schema(description = "文件业务类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileType,
        @Schema(description = "上传及后处理的统一展示阶段", requiredMode = Schema.RequiredMode.REQUIRED)
        FileProgressPhase phase,
        @Schema(description = "已确认上传的字节数", requiredMode = Schema.RequiredMode.REQUIRED)
        long uploadedBytes,
        @Schema(description = "预期文件总字节数", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalBytes,
        @Schema(description = "已确认上传分片数", requiredMode = Schema.RequiredMode.REQUIRED)
        int uploadedPartCount,
        @Schema(
                description = "分片总数；没有上传会话时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"integer", "null"})
        Integer partCount,
        @Schema(description = "上传百分比，100 不等于后处理已完成", requiredMode = Schema.RequiredMode.REQUIRED)
        double uploadPercent,
        @Schema(description = "文件及必要后处理是否已就绪", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ready,
        @Schema(description = "进度版本；用于识别进度更新，终态快照可为 0", requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(
                description = "进度更新时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime updatedAt,
        @Schema(description = "错误码；无错误时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String errorCode,
        @Schema(description = "失败原因；无错误时为空", requiredMode = Schema.RequiredMode.REQUIRED, types = {"string", "null"})
        String errorMessage) {
}
