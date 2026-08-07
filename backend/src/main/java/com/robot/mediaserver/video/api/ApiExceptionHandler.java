package com.robot.mediaserver.video.api;

import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.file.api.FileApiException;
import com.robot.mediaserver.file.service.FileStorageException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/**
 * API 全局异常处理器。
 *
 * @author leelay
 * @date 2026/05/19
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(FileApiException.class)
    public ResponseEntity<Map<String, Object>> handleFileApi(
            FileApiException ex,
            HttpServletRequest request) {
        return error(
                ex.getStatus(),
                ex.getCode(),
                ex.getMessage(),
                ex.isRetryable(),
                ex.getDetails(),
                request,
                ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS ? 60 : null);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Map<String, Object>> handleFileStorage(
            FileStorageException ex,
            HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("文件对象存储访问失败: requestId={}, path={}, reason={}",
                requestId, request.getRequestURI(), ex.getMessage(), ex);
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                "对象存储暂不可用，请稍后重试",
                true,
                Map.of(),
                request,
                5,
                requestId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), false, Map.of(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), false, Map.of(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数校验失败", false, Map.of(), request, null);
    }

    /**
     * Control API 调用 Media Internal API 时，保留下游业务错误状态和响应体。
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleUpstreamResponse(RestClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Map<String, Object> details,
            HttpServletRequest request,
            Integer retryAfterSeconds) {
        return error(status, code, message, retryable, details, request, retryAfterSeconds, requestId(request));
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Map<String, Object> details,
            HttpServletRequest request,
            Integer retryAfterSeconds,
            String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", DateTimeConfig.format(OffsetDateTime.now()));
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("retryable", retryable);
        body.put("requestId", requestId);
        body.put("path", request.getRequestURI());
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("X-Request-Id", requestId);
        if (retryAfterSeconds != null) {
            builder.header("Retry-After", String.valueOf(retryAfterSeconds));
        }
        return builder.body(body);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && requestId.matches("[A-Za-z0-9._-]{1,128}")) {
            return requestId;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
