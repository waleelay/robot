package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 单个文件的删除结果。
 *
 * @param fileId 文件 ID
 * @param success 是否删除成功
 * @param code 结果编码
 * @param message 结果说明
 */
public record FileDeleteResultResponse(
        @Schema(description = "文件 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String fileId,
        @Schema(description = "该项是否删除成功", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,
        @Schema(description = "业务结果码", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "业务结果说明", requiredMode = Schema.RequiredMode.REQUIRED)
        String message) {
}
