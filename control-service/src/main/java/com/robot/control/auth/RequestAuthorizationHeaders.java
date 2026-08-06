package com.robot.control.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 为当前 HTTP 请求发起的下游调用透传 Bearer Token。 */
@Component
public class RequestAuthorizationHeaders {

    /**
     * 将当前入站请求的 Bearer Token 写入下游请求头。
     *
     * @param headers 下游请求头
     */
    public void apply(HttpHeaders headers) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}
