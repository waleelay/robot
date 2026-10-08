package com.robot.mediaserver.file.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 文件接口业务异常，保留调用方可处理的错误码和上下文。
 */
public class FileApiException extends RuntimeException {

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
     * 可选错误上下文；内容由错误码决定。
     */
    private final Map<String, Object> details;

    /**
     * 初始化 FileApiException，保存所需依赖及初始运行状态。
     * @param status 对外返回的 HTTP 错误状态码
     * @param code 业务错误码
     * @param message 消息内容
     */
    public FileApiException(HttpStatus status, String code, String message) {
        this(status, code, message, false, Map.of());
    }

    /**
     * 初始化 FileApiException，保存所需依赖及初始运行状态。
     * @param status 对外返回的 HTTP 错误状态码
     * @param code 业务错误码
     * @param message 消息内容
     * @param retryable 是否允许重试
     * @param details 可选错误上下文；内容由错误码决定
     */
    public FileApiException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Map<String, Object> details) {
        super(message == null ? code : message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.details = details == null ? Map.of() : Map.copyOf(details);
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

    /**
     * 读取{@link #details}。
     *
     * @return 当前值，含义与约束见{@link #details}
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
