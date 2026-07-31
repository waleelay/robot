package com.robot.bigscreen.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedRequestHeadersTest {

    private final AuthenticatedRequestHeaders requestHeaders = new AuthenticatedRequestHeaders("bigscreen-web");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void replacesBrowserIdentityHeadersWithJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject("user-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("org_id", "org-001")
                .claim("azp", "bigscreen-web")
                .claim("realm_access", Map.of("roles", List.of("MEDIA_VIEWER")))
                .claim("resource_access", Map.of(
                        "bigscreen-web", Map.of("roles", List.of("MEDIA_OPERATOR", "EQUIPMENT_OPERATOR"))))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "spoofed-user");
        headers.set("X-Roles", "spoofed-role");

        requestHeaders.apply(headers);

        assertEquals("Bearer access-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("user-subject", headers.getFirst("X-User-Id"));
        assertEquals("org-001", headers.getFirst("X-Org-Id"));
        assertEquals("EQUIPMENT_OPERATOR,MEDIA_OPERATOR,MEDIA_VIEWER", headers.getFirst("X-Roles"));
    }

    @Test
    void removesUntrustedIdentityWhenRequestIsAnonymous() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "spoofed-user");
        headers.set("X-Roles", "spoofed-role");

        requestHeaders.apply(headers);

        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-Roles"));
    }

    @Test
    void readsRolesFromConfiguredClientWhenAuthorizedPartyDiffers() {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject("user-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("azp", "token-exchange-client")
                .claim("resource_access", Map.of(
                        "token-exchange-client", Map.of("roles", List.of("UNTRUSTED_ROLE")),
                        "bigscreen-web", Map.of("roles", List.of("MEDIA_VIEWER"))))
                .build();
        HttpHeaders headers = new HttpHeaders();

        requestHeaders.apply(headers, new JwtAuthenticationToken(jwt));

        assertEquals("MEDIA_VIEWER", headers.getFirst("X-Roles"));
    }
}
