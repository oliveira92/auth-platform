package com.authplatform.auth.domain.model;

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
    public static AuditEvent auth(String eventType,
                                  String actor,
                                  String subject,
                                  String applicationId,
                                  String outcome,
                                  String clientIp,
                                  Map<String, Object> metadata) {
        return new AuditEvent(
            eventType,
            "auth-service",
            actor,
            subject,
            applicationId,
            "authentication",
            eventType,
            outcome,
            clientIp,
            null,
            metadata == null ? Map.of() : metadata,
            Instant.now()
        );
    }
}
