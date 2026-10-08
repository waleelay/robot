package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 已存储分片的编号、ETag 和字节数，供断点续传跳过已完成分片。
 *
 * @param partNumber 从 1 开始的分片编号
 * @param etag 对象存储分片 ETag
 * @param size 该分片实际字节数
 */
public record FilePartInfoResponse(
        @Schema(description = "从 1 开始的分片编号", requiredMode = Schema.RequiredMode.REQUIRED)
        int partNumber,
        @Schema(description = "对象存储分片 ETag", requiredMode = Schema.RequiredMode.REQUIRED)
        String etag,
        @Schema(description = "该分片实际字节数", requiredMode = Schema.RequiredMode.REQUIRED)
        long size) {
}
