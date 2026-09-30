package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 上传会话及续传信息；来源文件已完成时 uploadId、expiresAt 为空，分片数量为零。
 *
 * @param fileId 文件 ID
 * @param uploadId 平台上传会话 ID；来源文件已完成时为空
 * @param uploadMode 上传方式
 * @param status 文件处理状态
 * @param partSize 每片规划字节数；已完成时为 0
 * @param partCount 分片总数；来源文件已完成且无需上传时为 0
 * @param uploadedParts 已完成分片列表
 * @param partUrls 本次签发的上传地址列表
 * @param expiresAt 本次返回的有效期；已完成且无需上传时可为空
 */
public record FileUploadResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(
                description = "平台上传会话 ID；来源文件已完成时为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"})
        String uploadId,
        @Schema(description = "上传方式", requiredMode = Schema.RequiredMode.REQUIRED)
        String uploadMode,
        @Schema(description = "文件处理状态", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
        @Schema(description = "每片规划字节数；已完成时为 0", requiredMode = Schema.RequiredMode.REQUIRED)
        long partSize,
        @Schema(description = "分片总数；来源文件已完成且无需上传时为 0", requiredMode = Schema.RequiredMode.REQUIRED)
        int partCount,
        @Schema(description = "已完成分片列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FilePartInfoResponse> uploadedParts,
        @Schema(description = "本次签发的上传地址列表", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FilePartUploadUrlResponse> partUrls,
        @Schema(
                description = "本次返回的有效期；已完成且无需上传时可为空",
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime expiresAt) {
}
