package com.authplatform.authorization.domain.model;

import java.time.Instant;
import java.util.List;

public record Role(
    String id,
    String name,
    String description,
    String applicationId,
    List<Permission> permissions,
    Instant createdAt
) {}
