package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.AuditEvent;
import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.in.CreatePermissionCommand;
import com.authplatform.authorization.domain.port.in.ManagePermissionUseCase;
import com.authplatform.authorization.domain.port.out.AuditEventPort;
import com.authplatform.authorization.domain.port.out.PermissionRepository;
import com.authplatform.authorization.domain.port.out.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagePermissionUseCaseImpl implements ManagePermissionUseCase {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AuditEventPort auditEventPort;

    @Override
    public Permission createPermission(CreatePermissionCommand command) {
        permissionRepository.findByResourceAndAction(command.resource(), command.action())
            .ifPresent(existing -> {
                throw new IllegalArgumentException(
                    "Permission already exists for resource/action: " + existing.toScope());
            });

        Permission permission = new Permission(
            UUID.randomUUID().toString(),
            command.name(),
            command.description(),
            command.resource(),
            command.action()
        );

        Permission saved = permissionRepository.save(permission);
        auditEventPort.publish(AuditEvent.authorization(
            "PERMISSION_CREATED",
            "admin-api",
            saved.id(),
            null,
            saved.resource(),
            saved.action(),
            "SUCCESS",
            Map.of("scope", saved.toScope())
        ));
        log.info("Permission created: {}", saved.toScope());
        return saved;
    }

    @Override
    public Permission updatePermission(String permissionId, CreatePermissionCommand command) {
        permissionRepository.findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));

        permissionRepository.findByResourceAndAction(command.resource(), command.action())
            .filter(existing -> !existing.id().equals(permissionId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException(
                    "Permission already exists for resource/action: " + existing.toScope());
            });

        Permission updated = new Permission(
            permissionId,
            command.name(),
            command.description(),
            command.resource(),
            command.action()
        );

        Permission saved = permissionRepository.save(updated);
        auditEventPort.publish(AuditEvent.authorization(
            "PERMISSION_UPDATED",
            "admin-api",
            saved.id(),
            null,
            saved.resource(),
            saved.action(),
            "SUCCESS",
            Map.of("scope", saved.toScope())
        ));
        log.info("Permission updated: {}", saved.toScope());
        return saved;
    }

    @Override
    public Permission getPermission(String permissionId) {
        return permissionRepository.findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));
    }

    @Override
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    @Override
    public void deletePermission(String permissionId) {
        permissionRepository.findById(permissionId)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + permissionId));
        permissionRepository.delete(permissionId);
        auditEventPort.publish(AuditEvent.authorization(
            "PERMISSION_DELETED",
            "admin-api",
            permissionId,
            null,
            "permission",
            "delete",
            "SUCCESS",
            Map.of()
        ));
        log.info("Permission deleted: {}", permissionId);
    }

    @Override
    public Role assignPermissionToRole(String roleId, String permissionId) {
        Role role = roleRepository.assignPermission(roleId, permissionId);
        auditEventPort.publish(AuditEvent.authorization(
            "ROLE_PERMISSION_ASSIGNED",
            "admin-api",
            roleId,
            role.applicationId(),
            "role-permission",
            "assign",
            "SUCCESS",
            Map.of("permissionId", permissionId)
        ));
        log.info("Permission {} assigned to role {}", permissionId, roleId);
        return role;
    }

    @Override
    public Role revokePermissionFromRole(String roleId, String permissionId) {
        Role role = roleRepository.revokePermission(roleId, permissionId);
        auditEventPort.publish(AuditEvent.authorization(
            "ROLE_PERMISSION_REVOKED",
            "admin-api",
            roleId,
            role.applicationId(),
            "role-permission",
            "revoke",
            "SUCCESS",
            Map.of("permissionId", permissionId)
        ));
        log.info("Permission {} revoked from role {}", permissionId, roleId);
        return role;
    }
}
