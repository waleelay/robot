package com.robot.control.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Map;

class RequestAuthorizationHeadersTest {

    private final RequestAuthorizationHeaders requestAuthorizationHeaders = new RequestAuthorizationHeaders();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        requestAuthorizationHeaders.clearWebSocketHeaders();
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
    void buildsStableCacheKeyFromTrustedIdentityAheadOfBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        request.addHeader("X-User-Id", "user-a");
        request.addHeader("X-Org-Id", "org-1");
        request.addHeader("X-Roles", "ROLE_A");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String cacheKey = requestAuthorizationHeaders.currentCacheKey().orElseThrow();

        assertThat(cacheKey).startsWith("headers:");
        assertThat(cacheKey).doesNotContain("access-token");
        assertThat(requestAuthorizationHeaders.currentCacheKey()).contains(cacheKey);
    }

    @Test
    void fallsBackToBearerCacheKeyWithoutTrustedIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String cacheKey = requestAuthorizationHeaders.currentCacheKey().orElseThrow();

        assertThat(cacheKey).startsWith("bearer:");
        assertThat(cacheKey).doesNotContain("access-token");
    }

    @Test
    void buildsDifferentCacheKeysForDifferentUsers() {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token-a");
        first.addHeader("X-User-Id", "user-a");
        first.addHeader("X-Org-Id", "org-1");
        first.addHeader("X-Roles", "ROLE_A");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        String firstCacheKey = requestAuthorizationHeaders.currentCacheKey().orElseThrow();

        MockHttpServletRequest second = new MockHttpServletRequest();
        second.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token-b");
        second.addHeader("X-User-Id", "user-b");
        second.addHeader("X-Org-Id", "org-1");
        second.addHeader("X-Roles", "ROLE_A");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(second));
        String secondCacheKey = requestAuthorizationHeaders.currentCacheKey().orElseThrow();

        assertThat(firstCacheKey).startsWith("headers:");
        assertThat(secondCacheKey).startsWith("headers:");
        assertThat(firstCacheKey).isNotEqualTo(secondCacheKey);
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
        assertThat(requestAuthorizationHeaders.currentCacheKey()).isEmpty();
    }

    @Test
    void doesNotBuildCacheKeyFromIdentityHeadersWithoutBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-a");
        request.addHeader("X-Org-Id", "org-1");
        request.addHeader("X-Roles", "ROLE_A");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(requestAuthorizationHeaders.currentCacheKey()).isEmpty();
    }

    @Test
    void doesNotBuildCacheKeyFromWebSocketIdentityHeadersWithoutBearerToken() {
        requestAuthorizationHeaders.setWebSocketHeaders(Map.of(
                "X-User-Id", "user-ws",
                "X-Org-Id", "org-ws",
                "X-Roles", "EQUIPMENT_OPERATOR"));

        assertThat(requestAuthorizationHeaders.currentCacheKey()).isEmpty();
    }

    @Test
    void usesWebSocketHeaderSnapshotWithoutServletRequest() {
        requestAuthorizationHeaders.setWebSocketHeaders(Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer websocket-token",
                "X-User-Id", "user-ws",
                "X-Org-Id", "org-ws",
                "X-Roles", "EQUIPMENT_OPERATOR"));
        HttpHeaders headers = new HttpHeaders();

        requestAuthorizationHeaders.apply(headers);

        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer websocket-token");
        assertThat(requestAuthorizationHeaders.currentCacheKey()).hasValueSatisfying(key ->
                assertThat(key).startsWith("headers:").doesNotContain("websocket-token"));
        requestAuthorizationHeaders.clearWebSocketHeaders();
    }
}
