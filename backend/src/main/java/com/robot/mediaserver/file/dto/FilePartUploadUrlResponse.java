package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 指定分片的限时 PUT 地址；地址由对象存储签名，调用方不得持久化或记录完整凭据。
 *
 * @param partNumber 从 1 开始的分片编号
 * @param uploadUrl 限时 PUT 地址，上传正文直接发送到对象存储
 */
public record FilePartUploadUrlResponse(
        @Schema(description = "从 1 开始的分片编号", requiredMode = Schema.RequiredMode.REQUIRED)
        int partNumber,
        @Schema(description = "限时 PUT 地址，上传正文直接发送到对象存储", requiredMode = Schema.RequiredMode.REQUIRED)
        String uploadUrl) {
}
