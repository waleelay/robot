package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 批量删除文件响应。
 *
 * @param total 请求总数
 * @param succeeded 成功数
 * @param failed 失败数
 * @param results 逐条删除结果
 */
public record FileBatchDeleteResponse(
        @Schema(description = "请求删除的文件数量", requiredMode = Schema.RequiredMode.REQUIRED)
        int total,
        @Schema(description = "成功删除数量", requiredMode = Schema.RequiredMode.REQUIRED)
        int succeeded,
        @Schema(description = "失败删除数量", requiredMode = Schema.RequiredMode.REQUIRED)
        int failed,
        @Schema(description = "与输入次序对应的逐项结果，允许部分失败", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FileDeleteResultResponse> results) {
}
