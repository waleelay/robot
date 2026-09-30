package com.robot.media.common.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 批量删除文件请求。
 *
 * @param fileIds 文件 ID 列表
 */
public record FileBatchDeleteRequest(
        @Schema(description = "文件 ID 数组")
        @NotEmpty @Size(max = 100) List<@NotBlank String> fileIds) {
}
