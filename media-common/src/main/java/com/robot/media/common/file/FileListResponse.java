package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分页文件列表响应。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param items 列表项
 * @param page 页码
 * @param size 分页大小
 * @param total 总数
 */
public record FileListResponse(
        @Schema(description = "本次返回的文件记录", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FileListItemResponse> items,
        @Schema(description = "从 0 开始的实际页码", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(description = "实际每页数量，范围 1 至 100", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "匹配记录总数", requiredMode = Schema.RequiredMode.REQUIRED)
        long total) {
}
