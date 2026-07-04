package com.robot.mediaserver.control.auth;

import java.util.Set;

public record CurrentUser(String userId, String orgId, Set<String> roles, String clientId) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
