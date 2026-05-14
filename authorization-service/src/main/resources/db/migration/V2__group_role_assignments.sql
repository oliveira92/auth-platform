-- Auth Platform - Authorization Service
-- V2: Group-based role assignments (Model 1 — LDAP as source of truth)
--
-- Replaces the user-centric assignment model with LDAP-group-centric assignments.
-- Permissions are now derived from the groups present in the JWT issued by auth-service,
-- which in turn reflect the user's actual membership in AD/LDAP at login time.

CREATE TABLE group_role_assignments (
    id             VARCHAR(36)  NOT NULL,
    ldap_group     VARCHAR(255) NOT NULL,
    role_id        VARCHAR(36)  NOT NULL,
    application_id VARCHAR(36)  NOT NULL,
    assigned_by    VARCHAR(255),
    assigned_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_group_role_app (ldap_group, role_id, application_id),
    CONSTRAINT fk_group_role_assignments_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_role_assignments_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE INDEX idx_group_role_ldap_group ON group_role_assignments(ldap_group);
CREATE INDEX idx_group_role_app        ON group_role_assignments(application_id);
