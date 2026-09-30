package com.robot.bigscreen.auth;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** 根据已认证的用户与客户端身份生成可信下游请求头。 */
@Component
public class AuthenticatedRequestHeaders {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "platform_admin",
            "super_admin",
            "admin");

    private static final Set<String> ADMIN_BUSINESS_ROLES = Set.of(
            "MEDIA_VIEWER",
            "MEDIA_OPERATOR",
            "EQUIPMENT_OPERATOR",
            "FIELD_OPERATOR");

    private static final Set<String> TRUSTED_USER_HEADERS = Set.of(
            "X-User-Id",
            "X-Org-Id",
            "X-Roles");

    private final Set<String> clientIds;

    /**
     * 初始化 AuthenticatedRequestHeaders，保存所需依赖及初始运行状态。
     *
     * @param clientId 客户端 ID
     * @param fieldCallClientId 允许现场呼叫的 JWT 客户端标识
     */
    public AuthenticatedRequestHeaders(
            @Value("${bigscreen.auth.client-id}") String clientId,
            @Value("${bigscreen.auth.field-call-client-id}") String fieldCallClientId) {
        this.clientIds = Set.of(clientId, fieldCallClientId);
    }

    /**
     * 从已认证上下文构造受信身份头，覆盖浏览器伪造值，并保留当前合法令牌供下游鉴权。
     * @param headers 即将发往受信下游的可写请求头
     */
    public void apply(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        apply(headers, authentication);
    }

    /**
     * 从已认证上下文构造受信身份头，覆盖浏览器伪造值，并保留当前合法令牌供下游鉴权。
     * @param headers 即将发往受信下游的可写请求头
     * @param authentication 经过认证的当前用户上下文
     */
    public void apply(HttpHeaders headers, Authentication authentication) {
        TRUSTED_USER_HEADERS.forEach(headers::remove);
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Request-Id", traceId);
        }
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
            for (String clientId : clientIds) {
                Object resource = resources.get(clientId);
                if (resource instanceof Map<?, ?> resourceClaims) {
                    addRoles(roles, resourceClaims.get("roles"));
                }
            }
        }
        if (roles.stream().anyMatch(ADMIN_ROLES::contains)) {
            roles.addAll(ADMIN_BUSINESS_ROLES);
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
