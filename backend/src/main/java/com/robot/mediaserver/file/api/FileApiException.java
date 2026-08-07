package com.robot.mediaserver.file.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 文件接口业务异常，保留调用方可处理的错误码和上下文。
 */
public class FileApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final Map<String, Object> details;

    public FileApiException(HttpStatus status, String code, String message) {
        this(status, code, message, false, Map.of());
    }

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

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
