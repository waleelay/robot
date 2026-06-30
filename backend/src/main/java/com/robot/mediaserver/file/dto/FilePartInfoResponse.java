package com.robot.mediaserver.file.dto;

public record FilePartInfoResponse(int partNumber, String etag, long size) {
}
