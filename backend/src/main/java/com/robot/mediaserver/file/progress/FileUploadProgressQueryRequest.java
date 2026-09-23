package com.robot.mediaserver.file.progress;

import jakarta.validation.constraints.Size;
import java.util.List;

public record FileUploadProgressQueryRequest(
        @Size(max = 500, message = "单次最多查询 500 个文件") List<String> fileIds) {
}
