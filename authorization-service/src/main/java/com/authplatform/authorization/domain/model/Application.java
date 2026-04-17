package com.authplatform.authorization.domain.model;

import java.time.Instant;
import java.util.List;

public record Application(
    String id,
    String name,
    String description,
    String clientId,
    ApplicationStatus status,
    String ownerTeam,
    List<String> allowedRoles,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isActive() {
        return ApplicationStatus.ACTIVE.equals(status);
    }
}
