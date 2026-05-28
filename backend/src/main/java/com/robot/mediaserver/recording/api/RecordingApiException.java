package com.robot.mediaserver.recording.api;

import org.springframework.http.HttpStatus;

public class RecordingApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RecordingApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
