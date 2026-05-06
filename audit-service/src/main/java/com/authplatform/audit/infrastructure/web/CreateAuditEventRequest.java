package com.authplatform.audit.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record CreateAuditEventRequest(
    @NotBlank String eventType,
    @NotBlank String sourceService,
    String actor,
    String subject,
    String applicationId,
    String resource,
    String action,
    @NotBlank String outcome,
    String clientIp,
    String correlationId,
    Map<String, Object> metadata,
    @JsonProperty("occurredAt") Instant occurredAt
) {}
