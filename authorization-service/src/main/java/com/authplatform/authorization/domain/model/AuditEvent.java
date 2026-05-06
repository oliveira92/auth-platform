package com.authplatform.authorization.domain.model;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
    String eventType,
    String sourceService,
    String actor,
    String subject,
    String applicationId,
    String resource,
    String action,
    String outcome,
    String clientIp,
    String correlationId,
    Map<String, Object> metadata,
    Instant occurredAt
) {
    public static AuditEvent authorization(String eventType,
                                           String actor,
                                           String subject,
                                           String applicationId,
                                           String resource,
                                           String action,
                                           String outcome,
                                           Map<String, Object> metadata) {
        return new AuditEvent(
            eventType,
            "authorization-service",
            actor,
            subject,
            applicationId,
            resource,
            action,
            outcome,
            null,
            null,
            metadata == null ? Map.of() : metadata,
            Instant.now()
        );
    }
}
