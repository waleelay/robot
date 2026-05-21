package com.robot.mediaserver.rtsp.dto;

public record RtspProbeResponse(boolean success, String url, String codec, Integer width, Integer height, String message) {
}
