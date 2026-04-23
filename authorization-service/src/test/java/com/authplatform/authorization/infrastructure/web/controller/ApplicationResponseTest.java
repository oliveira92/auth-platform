package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.model.ApplicationStatus;
import com.authplatform.authorization.infrastructure.config.AuthPlatformMetadataProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationResponseTest {

    @Test
    void shouldIncludeTokenValidationMetadataInApplicationResponse() {
        Instant now = Instant.parse("2026-04-22T12:00:00Z");
        Application app = new Application(
            "app-123",
            "portal-xpto",
            "Portal corporativo",
            "portal-xpto-a1b2c3d4",
            ApplicationStatus.ACTIVE,
            "time-rh",
            List.of("ROLE_ENGINEERS"),
            now,
            now
        );

        AuthPlatformMetadataProperties metadata = new AuthPlatformMetadataProperties();
        metadata.setIssuer("https://auth.empresa.com");
        metadata.setJwksUri("https://auth.empresa.com/.well-known/jwks.json");
        metadata.setIntrospectionUrl("https://auth.empresa.com/api/v1/auth/validate");
        metadata.setTokenAlgorithm("RS256");

        ApplicationResponse response = ApplicationResponse.from(app, metadata);

        assertEquals(app.id(), response.id());
        assertEquals(app.clientId(), response.clientId());
        assertEquals("https://auth.empresa.com", response.issuer());
        assertEquals("https://auth.empresa.com/.well-known/jwks.json", response.jwksUri());
        assertEquals("https://auth.empresa.com/api/v1/auth/validate", response.introspectionUrl());
        assertEquals("RS256", response.tokenAlgorithm());
    }
}
