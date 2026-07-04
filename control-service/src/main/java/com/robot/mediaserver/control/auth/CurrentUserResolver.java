package com.robot.mediaserver.control.auth;

import com.robot.mediaserver.control.config.ControlServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final ControlServiceProperties properties;

    public CurrentUserResolver(ControlServiceProperties properties) {
        this.properties = properties;
    }

    public CurrentUser resolve(HttpServletRequest request) {
        String userId = headerOrDefault(request, "X-User-Id", "dev-user");
        String orgId = headerOrDefault(request, "X-Org-Id", properties.getAuth().getDefaultOrgId());
        String clientId = headerOrDefault(request, "X-Client-Id", "web");
        String rolesHeader = headerOrDefault(request, "X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR,EQUIPMENT_OPERATOR");
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return new CurrentUser(userId, orgId, roles, clientId);
    }

    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
