package com.authplatform.authorization.domain.port.in;

public record CreateRoleCommand(
    String name,
    String description,
    String applicationId
) {}
