package com.robot.mediaserver.file.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 进度查询结果；不可识别的文件 ID 单独列入 missingFileIds，不伪造进度。
 *
 * @param items 本次返回的文件记录
 * @param missingFileIds 无法识别的文件 ID；与 items 分别返回
 * @param generatedAt 本次进度查询生成时间
 */
public record FileUploadProgressQueryResponse(
        @Schema(description = "本次返回的文件记录", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FileUploadProgressItem> items,
        @Schema(description = "无法识别的文件 ID；与 items 分别返回", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> missingFileIds,
        @Schema(
                description = "本次进度查询生成时间",
                requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = String.class,
                pattern = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$",
                example = "2026-09-29 12:00:00")
        OffsetDateTime generatedAt) {
}
