package com.authplatform.authorization.domain.port.in;

public record CreatePermissionCommand(
    String name,
    String description,
    String resource,
    String action
) {}
