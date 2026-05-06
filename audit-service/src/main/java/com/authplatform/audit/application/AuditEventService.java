package com.authplatform.audit.application;

import com.authplatform.audit.infrastructure.persistence.AuditEventEntity;
import com.authplatform.audit.infrastructure.persistence.AuditEventJpaRepository;
import com.authplatform.audit.infrastructure.web.AuditEventResponse;
import com.authplatform.audit.infrastructure.web.CreateAuditEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditEventService {

    private final AuditEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public AuditEventResponse record(CreateAuditEventRequest request) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setEventType(request.eventType());
        entity.setSourceService(request.sourceService());
        entity.setActor(request.actor());
        entity.setSubject(request.subject());
        entity.setApplicationId(request.applicationId());
        entity.setResource(request.resource());
        entity.setAction(request.action());
        entity.setOutcome(request.outcome());
        entity.setClientIp(request.clientIp());
        entity.setCorrelationId(request.correlationId());
        entity.setMetadataJson(writeMetadata(request));
        entity.setOccurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt());
        entity.setReceivedAt(Instant.now());

        return AuditEventResponse.from(repository.save(entity), objectMapper);
    }

    public AuditEventResponse get(String eventId) {
        return repository.findById(eventId)
            .map(entity -> AuditEventResponse.from(entity, objectMapper))
            .orElseThrow(() -> new IllegalArgumentException("Audit event not found: " + eventId));
    }

    public Page<AuditEventResponse> search(String eventType,
                                           String actor,
                                           String subject,
                                           String applicationId,
                                           String outcome,
                                           Instant from,
                                           Instant to,
                                           Pageable pageable) {
        Specification<AuditEventEntity> specification = Specification.where(equal("eventType", eventType))
            .and(equal("actor", actor))
            .and(equal("subject", subject))
            .and(equal("applicationId", applicationId))
            .and(equal("outcome", outcome))
            .and(after(from))
            .and(before(to));

        return repository.findAll(specification, pageable)
            .map(entity -> AuditEventResponse.from(entity, objectMapper));
    }

    private Specification<AuditEventEntity> equal(String attribute, String value) {
        return (root, query, cb) -> value == null || value.isBlank()
            ? cb.conjunction()
            : cb.equal(root.get(attribute), value);
    }

    private Specification<AuditEventEntity> after(Instant from) {
        return (root, query, cb) -> from == null
            ? cb.conjunction()
            : cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    private Specification<AuditEventEntity> before(Instant to) {
        return (root, query, cb) -> to == null
            ? cb.conjunction()
            : cb.lessThanOrEqualTo(root.get("occurredAt"), to);
    }

    private String writeMetadata(CreateAuditEventRequest request) {
        if (request.metadata() == null || request.metadata().isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(request.metadata());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid audit metadata", e);
        }
    }
}
