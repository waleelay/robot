package com.robot.mediaserver.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FileBatchDeleteRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank String> fileIds) {
}
