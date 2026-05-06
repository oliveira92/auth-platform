package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.AuditEvent;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.model.UserRoleAssignment;
import com.authplatform.authorization.domain.port.in.CreateRoleCommand;
import com.authplatform.authorization.domain.port.in.ManageRoleUseCase;
import com.authplatform.authorization.domain.port.out.AuditEventPort;
import com.authplatform.authorization.domain.port.out.RoleRepository;
import com.authplatform.authorization.domain.port.out.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManageRoleUseCaseImpl implements ManageRoleUseCase {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditEventPort auditEventPort;

    @Override
    public Role createRole(CreateRoleCommand command) {
        Role role = new Role(
            UUID.randomUUID().toString(),
            command.name(),
            command.description(),
            command.applicationId(),
            List.of(),
            Instant.now()
        );

        Role saved = roleRepository.save(role);
        auditEventPort.publish(AuditEvent.authorization(
            "ROLE_CREATED",
            "admin-api",
            saved.id(),
            saved.applicationId(),
            "role",
            "create",
            "SUCCESS",
            Map.of("name", saved.name())
        ));
        log.info("Role created: {} for application: {}", saved.name(), saved.applicationId());
        return saved;
    }

    @Override
    public void assignRoleToUser(String username, String roleId, String applicationId, String assignedBy) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));

        UserRoleAssignment assignment = new UserRoleAssignment(
            UUID.randomUUID().toString(),
            username,
            roleId,
            applicationId,
            assignedBy,
            Instant.now(),
            null
        );

        userRoleRepository.save(assignment);
        auditEventPort.publish(AuditEvent.authorization(
            "ROLE_ASSIGNED",
            assignedBy,
            username,
            applicationId,
            "role",
            "assign",
            "SUCCESS",
            Map.of("roleId", roleId)
        ));
        log.info("Role {} assigned to user {} in application {} by {}", roleId, username, applicationId, assignedBy);
    }

    @Override
    public void revokeRoleFromUser(String username, String roleId, String applicationId) {
        userRoleRepository.delete(username, roleId, applicationId);
        auditEventPort.publish(AuditEvent.authorization(
            "ROLE_REVOKED",
            "admin-api",
            username,
            applicationId,
            "role",
            "revoke",
            "SUCCESS",
            Map.of("roleId", roleId)
        ));
        log.info("Role {} revoked from user {} in application {}", roleId, username, applicationId);
    }

    @Override
    public List<Role> getRolesForApplication(String applicationId) {
        return roleRepository.findByApplicationId(applicationId);
    }
}
