package com.robot.control.auth;

import java.util.Set;

/**
 * 当前请求用户上下文。
 *
 * @author leelay
 * @date 2026-07-05
 *
 * @param userId 用户 ID
 * @param orgId 组织 ID
 * @param roles 角色集合
 * @param clientId 客户端 ID
 */
public record CurrentUser(String userId, String orgId, Set<String> roles, String clientId) {

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * @param role 角色名
     * @return 是否拥有该角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
