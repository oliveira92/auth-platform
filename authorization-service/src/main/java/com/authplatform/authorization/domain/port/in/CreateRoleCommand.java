package com.authplatform.authorization.domain.port.in;

import java.util.List;

public record CreateRoleCommand(
    String name,
    String description,
    String applicationId,
    List<String> permissionIds
) {}
