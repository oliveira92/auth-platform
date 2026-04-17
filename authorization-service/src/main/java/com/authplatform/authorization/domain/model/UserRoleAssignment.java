package com.authplatform.authorization.domain.model;

import java.time.Instant;

public record UserRoleAssignment(
    String id,
    String username,
    String roleId,
    String applicationId,
    String assignedBy,
    Instant assignedAt,
    Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
