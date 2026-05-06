-- Auth Platform - Audit Service Initial Schema

CREATE TABLE audit_events (
    id             VARCHAR(36)  PRIMARY KEY,
    event_type     VARCHAR(120) NOT NULL,
    source_service VARCHAR(120) NOT NULL,
    actor          VARCHAR(255),
    subject        VARCHAR(255),
    application_id VARCHAR(255),
    resource       VARCHAR(255),
    action         VARCHAR(120),
    outcome        VARCHAR(50)  NOT NULL,
    client_ip      VARCHAR(100),
    correlation_id VARCHAR(120),
    metadata_json  TEXT,
    occurred_at    DATETIME(6)  NOT NULL,
    received_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_audit_events_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_actor ON audit_events(actor);
CREATE INDEX idx_audit_events_subject ON audit_events(subject);
CREATE INDEX idx_audit_events_application ON audit_events(application_id);
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at);
