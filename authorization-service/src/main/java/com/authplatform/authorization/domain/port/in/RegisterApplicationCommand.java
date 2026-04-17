package com.authplatform.authorization.domain.port.in;

import java.util.List;

public record RegisterApplicationCommand(
    String name,
    String description,
    String ownerTeam,
    List<String> allowedRoles
) {}
