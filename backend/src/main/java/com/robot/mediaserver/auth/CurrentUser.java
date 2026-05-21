package com.robot.mediaserver.auth;

import java.util.Set;

/**
 * 当前请求用户上下文。
 *
 * @author leelay
 * @date 2026/05/19
 */
public record CurrentUser(String userId, String orgId, Set<String> roles, String clientId) {

    /**
     * 判断当前用户是否具备指定角色。
     *
     * @param role 角色编码
     * @return 是否具备角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
