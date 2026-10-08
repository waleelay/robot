package com.robot.mediaserver.video.service;

import org.springframework.http.HttpStatus;

/** 固定摄像头 Ingress 生命周期业务异常。 */
public class FixedCameraIngressException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * 对外返回的 HTTP 错误状态码。
     */
    private final HttpStatus status;
    /**
     * 业务错误码。
     */
    private final String code;
    /**
     * 是否允许重试。
     */
    private final boolean retryable;

    /**
     * 初始化 FixedCameraIngressException，保存所需依赖及初始运行状态。
     * @param status 对外返回的 HTTP 错误状态码
     * @param code 业务错误码
     * @param message 消息内容
     * @param retryable 是否允许重试
     */
    public FixedCameraIngressException(HttpStatus status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    /**
     * 读取{@link #status}。
     *
     * @return 当前值，含义与约束见{@link #status}
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 读取{@link #code}。
     *
     * @return 当前值，含义与约束见{@link #code}
     */
    public String getCode() {
        return code;
    }

    /**
     * 读取{@link #retryable}。
     *
     * @return 当前值，含义与约束见{@link #retryable}
     */
    public boolean isRetryable() {
        return retryable;
    }
}
