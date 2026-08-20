package com.robot.control.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 为当前 HTTP 请求发起的下游调用透传 Bearer Token。 */
@Component
public class RequestAuthorizationHeaders {

    private static final ThreadLocal<Map<String, String>> WEBSOCKET_HEADERS = new ThreadLocal<>();

    /**
     * 在当前 WebSocket 命令线程设置握手认证头快照。
     *
     * @param headers 握手时复制的认证头
     */
    public void setWebSocketHeaders(Map<String, String> headers) {
        WEBSOCKET_HEADERS.set(headers == null ? Map.of() : Map.copyOf(headers));
    }

    /** 清理当前 WebSocket 命令线程的认证头，避免线程池线程污染后续请求。 */
    public void clearWebSocketHeaders() {
        WEBSOCKET_HEADERS.remove();
    }

    /**
     * 将当前入站请求的 Bearer Token 写入下游请求头。
     *
     * @param headers 下游请求头
     */
    public void apply(HttpHeaders headers) {
        Map<String, String> websocketHeaders = WEBSOCKET_HEADERS.get();
        if (websocketHeaders != null) {
            String authorization = websocketHeaders.get(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                headers.set(HttpHeaders.AUTHORIZATION, authorization);
            }
            return;
        }
        Optional<HttpServletRequest> currentRequest = currentRequest();
        if (currentRequest.isEmpty()) {
            return;
        }
        HttpServletRequest request = currentRequest.get();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    /**
     * 返回当前请求对应的缓存身份 key。
     *
     * @return 缓存身份 key
     */
    public Optional<String> currentCacheKey() {
        Map<String, String> websocketHeaders = WEBSOCKET_HEADERS.get();
        if (websocketHeaders != null) {
            return Optional.of(cacheKey(websocketHeaders));
        }
        return currentRequest().map(request -> {
            Map<String, String> headers = Map.of(
                    HttpHeaders.AUTHORIZATION, value(request.getHeader(HttpHeaders.AUTHORIZATION)),
                    "X-User-Id", value(request.getHeader("X-User-Id")),
                    "X-Org-Id", value(request.getHeader("X-Org-Id")),
                    "X-Roles", value(request.getHeader("X-Roles")));
            return cacheKey(headers);
        });
    }

    private String cacheKey(Map<String, String> headers) {
        String userId = headers.get("X-User-Id");
        String orgId = headers.get("X-Org-Id");
        String roles = headers.get("X-Roles");
        if (StringUtils.hasText(userId) || StringUtils.hasText(orgId) || StringUtils.hasText(roles)) {
            return "headers:" + sha256(value(userId) + "|" + value(orgId) + "|" + value(roles));
        }
        String authorization = headers.get(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "bearer:" + sha256(authorization);
        }
        return "anonymous";
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Optional.empty();
        }
        return Optional.of(attributes.getRequest());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
