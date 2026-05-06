package com.authplatform.audit.infrastructure.web;

import com.authplatform.audit.application.AuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit/events")
@Tag(name = "Audit Events", description = "Auth Platform audit trail")
public class AuditEventController {

    private final AuditEventService service;

    public AuditEventController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Record an audit event")
    public ResponseEntity<AuditEventResponse> record(@Valid @RequestBody CreateAuditEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(request));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get an audit event by id")
    public ResponseEntity<AuditEventResponse> get(@PathVariable String eventId) {
        return ResponseEntity.ok(service.get(eventId));
    }

    @GetMapping
    @Operation(summary = "Search audit events")
    public ResponseEntity<Page<AuditEventResponse>> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String applicationId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Pageable pageable) {
        return ResponseEntity.ok(service.search(eventType, actor, subject, applicationId, outcome, from, to, pageable));
    }
}
