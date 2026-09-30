package com.robot.mediaserver.auth;

import com.robot.mediaserver.config.MediaProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 解析受控上游传入的用户、组织、角色和客户端请求头。
 *
 *  <p>本类不校验 JWT；缺失请求头时仍使用现有开发回退值，生产网络须限制调用来源。</p>
 * @author leelay
 * @date 2026/05/19
 */
@Component
public class CurrentUserResolver {

    private final MediaProperties properties;

    /**
     * 初始化 CurrentUserResolver，保存所需依赖及初始运行状态。
     *
     * @param properties 服务配置
     */
    public CurrentUserResolver(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 从 HTTP 请求头解析当前用户。
     *
     * @param request HTTP 请求
     * @return 当前用户上下文
     */
    public CurrentUser resolve(HttpServletRequest request) {
        String userId = headerOrDefault(request, "X-User-Id", "dev-user");
        String orgId = headerOrDefault(request, "X-Org-Id", properties.getFile().getDefaultOrgId());
        String clientId = headerOrDefault(request, "X-Client-Id", "web");
        String rolesHeader = headerOrDefault(request, "X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR");
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
