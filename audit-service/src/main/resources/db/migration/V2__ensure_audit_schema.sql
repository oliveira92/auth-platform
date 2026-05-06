-- Ensures audit schema exists when Flyway baselines a shared, non-empty database at version 1.

CREATE TABLE IF NOT EXISTS audit_events (
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
    received_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_events_type (event_type),
    INDEX idx_audit_events_actor (actor),
    INDEX idx_audit_events_subject (subject),
    INDEX idx_audit_events_application (application_id),
    INDEX idx_audit_events_occurred_at (occurred_at)
);
