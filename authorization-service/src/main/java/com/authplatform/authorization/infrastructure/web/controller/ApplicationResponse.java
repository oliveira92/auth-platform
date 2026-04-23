package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.model.ApplicationStatus;
import com.authplatform.authorization.infrastructure.config.AuthPlatformMetadataProperties;

import java.time.Instant;
import java.util.List;

public record ApplicationResponse(
    String id,
    String name,
    String description,
    String clientId,
    ApplicationStatus status,
    String ownerTeam,
    List<String> allowedRoles,
    Instant createdAt,
    Instant updatedAt,
    String issuer,
    String jwksUri,
    String introspectionUrl,
    String tokenAlgorithm
) {
    public static ApplicationResponse from(Application app, AuthPlatformMetadataProperties authMetadata) {
        return new ApplicationResponse(
            app.id(),
            app.name(),
            app.description(),
            app.clientId(),
            app.status(),
            app.ownerTeam(),
            app.allowedRoles(),
            app.createdAt(),
            app.updatedAt(),
            authMetadata.getIssuer(),
            authMetadata.getJwksUri(),
            authMetadata.getIntrospectionUrl(),
            authMetadata.getTokenAlgorithm()
        );
    }
}
