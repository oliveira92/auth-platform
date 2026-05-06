-- Auth Platform - Authorization Service Initial Schema
-- V1: Core tables for applications, roles, permissions and assignments

CREATE TABLE applications (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    client_id   VARCHAR(255) NOT NULL UNIQUE,
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    owner_team  VARCHAR(255),
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE application_allowed_roles (
    application_id VARCHAR(36)  NOT NULL,
    role_name      VARCHAR(255) NOT NULL,
    PRIMARY KEY (application_id, role_name),
    CONSTRAINT fk_application_allowed_roles_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE TABLE permissions (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    resource    VARCHAR(255) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    UNIQUE (resource, action)
);

CREATE TABLE roles (
    id             VARCHAR(36)  PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    description    TEXT,
    application_id VARCHAR(36)  NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE (name, application_id),
    CONSTRAINT fk_roles_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id       VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE TABLE user_role_assignments (
    id             VARCHAR(36)  PRIMARY KEY,
    username       VARCHAR(255) NOT NULL,
    role_id        VARCHAR(36)  NOT NULL,
    application_id VARCHAR(36)  NOT NULL,
    assigned_by    VARCHAR(255),
    assigned_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at     DATETIME(6),
    UNIQUE (username, role_id, application_id),
    CONSTRAINT fk_user_role_assignments_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_assignments_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_role_username ON user_role_assignments(username);
CREATE INDEX idx_user_role_app ON user_role_assignments(application_id);
CREATE INDEX idx_roles_app ON roles(application_id);
