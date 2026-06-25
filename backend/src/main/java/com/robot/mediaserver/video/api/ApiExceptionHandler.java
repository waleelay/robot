package com.robot.mediaserver.video.api;

import com.robot.mediaserver.config.DateTimeConfig;
import com.robot.mediaserver.recording.api.RecordingApiException;
import java.time.OffsetDateTime;
import java.util.Map;
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

    @ExceptionHandler(RecordingApiException.class)
    public ResponseEntity<Map<String, Object>> handleRecording(RecordingApiException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
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

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "code", code,
                "message", message));
    }
}
