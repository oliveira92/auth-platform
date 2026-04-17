package com.authplatform.auth.domain.model;

import java.util.List;
import java.util.Map;

public record User(
    String username,
    String email,
    String displayName,
    String department,
    List<String> groups,
    List<String> roles,
    Map<String, String> attributes
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isMemberOf(String group) {
        return groups != null && groups.contains(group);
    }
}
