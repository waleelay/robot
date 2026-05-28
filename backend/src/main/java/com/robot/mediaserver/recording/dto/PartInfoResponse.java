package com.robot.mediaserver.recording.dto;

public record PartInfoResponse(int partNumber, String etag, long size) {
}
