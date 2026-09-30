package com.robot.control.api;

import com.robot.control.call.IntercomBusyException;
import com.robot.control.config.DateTimeConfig;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/** 在控制服务对外接口边界保留下游媒体服务的失败状态与响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 将对讲占用异常映射为冲突响应，保留稳定业务码。
     *
     * @param ex 需要映射到响应的原始异常
     * @return HTTP 409 对讲冲突响应
     */
    @ExceptionHandler(IntercomBusyException.class)
    public ResponseEntity<Map<String, Object>> handleIntercomBusy(IntercomBusyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "code", ex.code(),
                "message", ex.getMessage()));
    }

    /**
     * 保留上游失败状态与正文，错误响应头不保证完整透传。
     *
     * @param ex 需要映射到响应的原始异常
     * @return 上游错误的代理响应
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<String> handleUpstreamResponse(RestClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
    }

    /**
     * 将参数或业务前置条件失败映射为本服务现有错误格式。
     *
     * @param ex 需要映射到响应的原始异常
     * @return 参数校验失败响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", DateTimeConfig.format(OffsetDateTime.now()),
                "code", "INVALID_CONTROL_REQUEST",
                "message", ex.getMessage()));
    }
}
