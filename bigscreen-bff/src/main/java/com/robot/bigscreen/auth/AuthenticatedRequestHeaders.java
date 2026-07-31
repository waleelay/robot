package com.robot.bigscreen.auth;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedRequestHeaders {

    private static final Set<String> TRUSTED_USER_HEADERS = Set.of(
            "X-User-Id",
            "X-Org-Id",
            "X-Roles");

    private final String clientId;

    public AuthenticatedRequestHeaders(@Value("${bigscreen.auth.client-id}") String clientId) {
        this.clientId = clientId;
    }

    public void apply(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        apply(headers, authentication);
    }

    public void apply(HttpHeaders headers, Authentication authentication) {
        TRUSTED_USER_HEADERS.forEach(headers::remove);
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return;
        }

        Jwt jwt = jwtAuthentication.getToken();
        headers.setBearerAuth(jwt.getTokenValue());
        headers.set("X-User-Id", jwt.getSubject());

        String orgId = firstClaim(jwt, "org_id", "orgId", "organization_id", "tenant_id");
        if (orgId != null) {
            headers.set("X-Org-Id", orgId);
        }

        Set<String> roles = roles(jwt);
        headers.set("X-Roles", roles.isEmpty() ? "AUTHENTICATED" : String.join(",", roles));
    }

    private Set<String> roles(Jwt jwt) {
        Set<String> roles = new TreeSet<>();
        addRoles(roles, jwt.getClaim("roles"));

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> realm) {
            addRoles(roles, realm.get("roles"));
        }

        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> resources) {
            Object resource = resources.get(clientId);
            if (resource instanceof Map<?, ?> resourceClaims) {
                addRoles(roles, resourceClaims.get("roles"));
            }
        }
        return roles;
    }

    private void addRoles(Set<String> target, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .forEach(target::add);
        } else if (value instanceof String role && !role.isBlank()) {
            target.add(role.trim());
        }
    }

    private String firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            Object value = jwt.getClaim(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
