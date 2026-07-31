package com.robot.bigscreen.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void acceptsConfiguredAuthorizedParty() {
        Jwt jwt = jwt("bigscreen-web", List.of("account"));

        assertFalse(securityConfig.authorizedClientValidator("bigscreen-web").validate(jwt).hasErrors());
    }

    @Test
    void acceptsConfiguredAudience() {
        Jwt jwt = jwt("another-client", List.of("bigscreen-web"));

        assertFalse(securityConfig.authorizedClientValidator("bigscreen-web").validate(jwt).hasErrors());
    }

    @Test
    void rejectsTokenForAnotherClient() {
        Jwt jwt = jwt("admin-web", List.of("account"));

        assertTrue(securityConfig.authorizedClientValidator("bigscreen-web").validate(jwt).hasErrors());
    }

    private Jwt jwt(String authorizedParty, List<String> audience) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject("user-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .audience(audience)
                .claim("azp", authorizedParty)
                .build();
    }
}
