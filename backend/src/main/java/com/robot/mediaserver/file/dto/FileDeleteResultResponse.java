package com.robot.mediaserver.file.dto;

public record FileDeleteResultResponse(
        String fileId,
        boolean success,
        String code,
        String message) {
}
