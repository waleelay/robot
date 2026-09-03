package com.robot.bigscreen.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 大屏 BFF 统一 API 异常处理。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccess(
            ResourceAccessException exception,
            HttpServletRequest request) {
        log.warn("BFF 代理下游服务失败，请求地址={}", request.getRequestURI(), exception);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", TIME_FORMATTER.format(LocalDateTime.now()));
        body.put("status", HttpStatus.BAD_GATEWAY.value());
        body.put("code", "UPSTREAM_UNAVAILABLE");
        body.put("message", "下游服务暂不可用，请稍后重试");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleRestClient(
            RestClientException exception,
            HttpServletRequest request) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                log.warn("BFF 下游服务认证失败，请求地址={}", request.getRequestURI());
                return errorResponse(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "登录状态无效或已过期",
                        request);
            }
            if (status == HttpStatus.FORBIDDEN.value()) {
                log.warn("BFF 下游服务拒绝当前用户访问，请求地址={}", request.getRequestURI());
                return errorResponse(
                        HttpStatus.FORBIDDEN,
                        "AUTHZ_DENIED",
                        "当前用户没有访问所需业务资源的权限",
                        request);
            }
        }
        log.warn("BFF 代理下游服务异常，请求地址={}", request.getRequestURI(), exception);
        return errorResponse(
                HttpStatus.BAD_GATEWAY,
                "UPSTREAM_ERROR",
                "下游服务请求失败，请稍后重试",
                request);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", TIME_FORMATTER.format(LocalDateTime.now()));
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
