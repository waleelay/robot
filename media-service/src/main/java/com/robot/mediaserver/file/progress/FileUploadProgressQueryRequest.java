package com.robot.mediaserver.file.progress;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量查询文件进度；最多 500 个 ID，缺省或空集合由服务按空查询处理。
 *
 * @param fileIds 查询文件 ID，最多 500 个；未传、null 或空数组返回空结果
 */
public record FileUploadProgressQueryRequest(
        @Schema(description = "查询文件 ID，最多 500 个；未传、null 或空数组返回空结果", types = {"array", "null"})
        @Size(max = 500, message = "单次最多查询 500 个文件") List<String> fileIds) {
}
