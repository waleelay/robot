package com.robot.mediaserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 开发阶段 Mock Auth 解析器。
 *
 * <p>第一版从请求头读取用户、组织和角色；后续接入平台 Auth/JWT 时，
 * 只替换该层，业务服务继续使用 CurrentUser。</p>
 *
 * @author leelay
 * @date 2026/05/19
 */
@Component
public class CurrentUserResolver {

    /**
     * 从 HTTP 请求头解析当前用户。
     *
     * @param request HTTP 请求
     * @return 当前用户上下文
     */
    public CurrentUser resolve(HttpServletRequest request) {
        String userId = headerOrDefault(request, "X-User-Id", "dev-user");
        String orgId = headerOrDefault(request, "X-Org-Id", "dev-org");
        String rolesHeader = headerOrDefault(request, "X-Roles", "MEDIA_VIEWER,MEDIA_OPERATOR");
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());
        return new CurrentUser(userId, orgId, roles);
    }

    private String headerOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
