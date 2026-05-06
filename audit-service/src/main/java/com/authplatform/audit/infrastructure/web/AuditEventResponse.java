package com.authplatform.audit.infrastructure.web;

import com.authplatform.audit.infrastructure.persistence.AuditEventEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(
    String id,
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
    Instant occurredAt,
    Instant receivedAt
) {
    public static AuditEventResponse from(AuditEventEntity entity, ObjectMapper objectMapper) {
        return new AuditEventResponse(
            entity.getId(),
            entity.getEventType(),
            entity.getSourceService(),
            entity.getActor(),
            entity.getSubject(),
            entity.getApplicationId(),
            entity.getResource(),
            entity.getAction(),
            entity.getOutcome(),
            entity.getClientIp(),
            entity.getCorrelationId(),
            readMetadata(entity.getMetadataJson(), objectMapper),
            entity.getOccurredAt(),
            entity.getReceivedAt()
        );
    }

    private static Map<String, Object> readMetadata(String metadataJson, ObjectMapper objectMapper) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of("raw", metadataJson);
        }
    }
}
