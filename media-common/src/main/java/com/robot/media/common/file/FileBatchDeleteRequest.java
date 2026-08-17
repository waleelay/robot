package com.robot.media.common.file;

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
        @NotEmpty @Size(max = 100) List<@NotBlank String> fileIds) {
}
