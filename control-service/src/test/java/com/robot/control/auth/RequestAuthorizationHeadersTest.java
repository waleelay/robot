package com.robot.control.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestAuthorizationHeadersTest {

    private final RequestAuthorizationHeaders requestAuthorizationHeaders = new RequestAuthorizationHeaders();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsBearerTokenFromCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        HttpHeaders headers = new HttpHeaders();

        requestAuthorizationHeaders.apply(headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
    }

    @Test
    void ignoresMissingRequestContextAndNonBearerAuthorization() {
        HttpHeaders headers = new HttpHeaders();
        requestAuthorizationHeaders.apply(headers);
        assertThat(headers).doesNotContainKey(HttpHeaders.AUTHORIZATION);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic credentials");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        requestAuthorizationHeaders.apply(headers);

        assertThat(headers).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }
}
