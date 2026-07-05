package com.robot.control.auth;

import com.robot.control.config.ControlServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 从 HTTP 请求头解析当前用户上下文。
 *
 * @author leelay
 * @date 2026-07-05
 */
@Component
public class CurrentUserResolver {

    private final ControlServiceProperties properties;

    /**
     * 创建 CurrentUserResolver 实例。
     *
     * @param properties 服务配置
     */
    public CurrentUserResolver(ControlServiceProperties properties) {
        this.properties = properties;
    }

    /**
     * 从请求头解析当前用户。
     *
     * @param request 请求参数
     * @return 当前用户上下文
     */
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

    /**
     * 读取请求头，缺省时返回默认值。
     *
     * @param request 请求参数
     * @param name 名称
     * @param defaultValue 默认值
     * @return 请求头值或默认值
     */
    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
