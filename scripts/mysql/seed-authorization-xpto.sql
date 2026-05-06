-- Auth Platform - Authorization Service sample data
-- Scenario: Portal XPTO
--
-- This script is idempotent and can be executed multiple times.
-- It creates:
--   - application: portal-xpto
--   - permissions: beneficios/perfil/folha/configuracoes
--   - roles: XPTO_USER, XPTO_RH, XPTO_ADMIN
--   - user assignments: john.doe, jane.smith, admin.user

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- Application
-- ---------------------------------------------------------------------------

SET @seed_app_id = '11111111-1111-1111-1111-111111111111';
SET @app_client_id = 'portal-xpto-a1b2c3d4';

INSERT INTO applications (
    id,
    name,
    description,
    client_id,
    status,
    owner_team,
    created_at,
    updated_at
) VALUES (
    @seed_app_id,
    'portal-xpto',
    'Portal corporativo XPTO usado para validar o fluxo RBAC da Auth Platform',
    @app_client_id,
    'ACTIVE',
    'time-rh',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    client_id = VALUES(client_id),
    status = VALUES(status),
    owner_team = VALUES(owner_team),
    updated_at = CURRENT_TIMESTAMP(6);

SET @app_id = (
    SELECT id
    FROM applications
    WHERE client_id = @app_client_id
       OR name = 'portal-xpto'
    LIMIT 1
);

INSERT IGNORE INTO application_allowed_roles (application_id, role_name) VALUES
    (@app_id, 'ROLE_ENGINEERS'),
    (@app_id, 'ROLE_PLATFORM_TEAM'),
    (@app_id, 'ROLE_ADMINISTRATORS');

-- ---------------------------------------------------------------------------
-- Permission catalog
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, name, description, resource, action) VALUES
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
        'beneficios:read',
        'Permite visualizar o menu e os dados de benefícios',
        'beneficios',
        'read'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
        'perfil:read',
        'Permite visualizar dados básicos do próprio perfil',
        'perfil',
        'read'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
        'folha:read',
        'Permite visualizar informações de folha de pagamento',
        'folha',
        'read'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4',
        'folha:write',
        'Permite alterar informações de folha de pagamento',
        'folha',
        'write'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5',
        'configuracoes:read',
        'Permite visualizar configurações administrativas',
        'configuracoes',
        'read'
    ),
    (
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6',
        'configuracoes:write',
        'Permite alterar configurações administrativas',
        'configuracoes',
        'write'
    )
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description);

SET @perm_beneficios_read = (
    SELECT id FROM permissions WHERE resource = 'beneficios' AND action = 'read' LIMIT 1
);
SET @perm_perfil_read = (
    SELECT id FROM permissions WHERE resource = 'perfil' AND action = 'read' LIMIT 1
);
SET @perm_folha_read = (
    SELECT id FROM permissions WHERE resource = 'folha' AND action = 'read' LIMIT 1
);
SET @perm_folha_write = (
    SELECT id FROM permissions WHERE resource = 'folha' AND action = 'write' LIMIT 1
);
SET @perm_config_read = (
    SELECT id FROM permissions WHERE resource = 'configuracoes' AND action = 'read' LIMIT 1
);
SET @perm_config_write = (
    SELECT id FROM permissions WHERE resource = 'configuracoes' AND action = 'write' LIMIT 1
);

-- ---------------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------------

INSERT INTO roles (id, name, description, application_id, created_at) VALUES
    (
        '22222222-2222-2222-2222-222222222221',
        'XPTO_USER',
        'Usuário comum do Portal XPTO',
        @app_id,
        CURRENT_TIMESTAMP(6)
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'XPTO_RH',
        'Usuário do time de RH do Portal XPTO',
        @app_id,
        CURRENT_TIMESTAMP(6)
    ),
    (
        '22222222-2222-2222-2222-222222222223',
        'XPTO_ADMIN',
        'Administrador do Portal XPTO',
        @app_id,
        CURRENT_TIMESTAMP(6)
    )
ON DUPLICATE KEY UPDATE
    description = VALUES(description);

SET @role_xpto_user = (
    SELECT id FROM roles WHERE application_id = @app_id AND name = 'XPTO_USER' LIMIT 1
);
SET @role_xpto_rh = (
    SELECT id FROM roles WHERE application_id = @app_id AND name = 'XPTO_RH' LIMIT 1
);
SET @role_xpto_admin = (
    SELECT id FROM roles WHERE application_id = @app_id AND name = 'XPTO_ADMIN' LIMIT 1
);

-- ---------------------------------------------------------------------------
-- Role to permission associations
-- ---------------------------------------------------------------------------

-- XPTO_USER: benefits and profile only.
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
    (@role_xpto_user, @perm_beneficios_read),
    (@role_xpto_user, @perm_perfil_read);

-- XPTO_RH: user permissions plus payroll read/write.
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
    (@role_xpto_rh, @perm_beneficios_read),
    (@role_xpto_rh, @perm_perfil_read),
    (@role_xpto_rh, @perm_folha_read),
    (@role_xpto_rh, @perm_folha_write);

-- XPTO_ADMIN: all permissions.
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
    (@role_xpto_admin, @perm_beneficios_read),
    (@role_xpto_admin, @perm_perfil_read),
    (@role_xpto_admin, @perm_folha_read),
    (@role_xpto_admin, @perm_folha_write),
    (@role_xpto_admin, @perm_config_read),
    (@role_xpto_admin, @perm_config_write);

-- ---------------------------------------------------------------------------
-- User role assignments
-- ---------------------------------------------------------------------------

INSERT INTO user_role_assignments (
    id,
    username,
    role_id,
    application_id,
    assigned_by,
    assigned_at,
    expires_at
) VALUES
    (
        '33333333-3333-3333-3333-333333333331',
        'john.doe',
        @role_xpto_user,
        @app_id,
        'seed-script',
        CURRENT_TIMESTAMP(6),
        NULL
    ),
    (
        '33333333-3333-3333-3333-333333333332',
        'jane.smith',
        @role_xpto_rh,
        @app_id,
        'seed-script',
        CURRENT_TIMESTAMP(6),
        NULL
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'admin.user',
        @role_xpto_admin,
        @app_id,
        'seed-script',
        CURRENT_TIMESTAMP(6),
        NULL
    )
ON DUPLICATE KEY UPDATE
    assigned_by = VALUES(assigned_by),
    expires_at = VALUES(expires_at);

COMMIT;

-- ---------------------------------------------------------------------------
-- Verification queries
-- ---------------------------------------------------------------------------

SELECT
    'application' AS item,
    id,
    name,
    client_id,
    status
FROM applications
WHERE id = @app_id;

SELECT
    'role' AS item,
    id,
    name,
    application_id
FROM roles
WHERE application_id = @app_id
ORDER BY name;

SELECT
    'role_permission' AS item,
    r.name AS role_name,
    CONCAT(p.resource, ':', p.action) AS permission
FROM roles r
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
WHERE r.application_id = @app_id
ORDER BY r.name, p.resource, p.action;

SELECT
    'user_assignment' AS item,
    ura.username,
    r.name AS role_name,
    ura.application_id
FROM user_role_assignments ura
JOIN roles r ON r.id = ura.role_id
WHERE ura.application_id = @app_id
ORDER BY ura.username;
