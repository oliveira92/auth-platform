-- Auth Platform - Authorization Service Initial Schema
-- V1: Core tables for applications, roles, permissions and assignments

CREATE TABLE applications (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    client_id   VARCHAR(255) NOT NULL UNIQUE,
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    owner_team  VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE application_allowed_roles (
    application_id VARCHAR(36)  NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    role_name      VARCHAR(255) NOT NULL,
    PRIMARY KEY (application_id, role_name)
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
    application_id VARCHAR(36)  NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (name, application_id)
);

CREATE TABLE role_permissions (
    role_id       VARCHAR(36) NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id VARCHAR(36) NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role_assignments (
    id             VARCHAR(36)  PRIMARY KEY,
    username       VARCHAR(255) NOT NULL,
    role_id        VARCHAR(36)  NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    application_id VARCHAR(36)  NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    assigned_by    VARCHAR(255),
    assigned_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMP WITH TIME ZONE,
    UNIQUE (username, role_id, application_id)
);

CREATE INDEX idx_user_role_username ON user_role_assignments(username);
CREATE INDEX idx_user_role_app ON user_role_assignments(application_id);
CREATE INDEX idx_roles_app ON roles(application_id);
