package com.robot.mediaserver.video.service;

import org.springframework.http.HttpStatus;

/** 固定摄像头 Ingress 生命周期业务异常。 */
public class FixedCameraIngressException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    public FixedCameraIngressException(HttpStatus status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
