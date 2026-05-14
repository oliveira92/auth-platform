package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.AuditEvent;
import com.authplatform.authorization.domain.model.GroupRoleAssignment;
import com.authplatform.authorization.domain.port.in.AssignGroupRoleCommand;
import com.authplatform.authorization.domain.port.in.ManageGroupRoleUseCase;
import com.authplatform.authorization.domain.port.out.AuditEventPort;
import com.authplatform.authorization.domain.port.out.GroupRoleRepository;
import com.authplatform.authorization.domain.port.out.RoleRepository;
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
public class ManageGroupRoleUseCaseImpl implements ManageGroupRoleUseCase {

    private final GroupRoleRepository groupRoleRepository;
    private final RoleRepository roleRepository;
    private final AuditEventPort auditEventPort;

    @Override
    public GroupRoleAssignment assignGroupToRole(AssignGroupRoleCommand command) {
        roleRepository.findById(command.roleId())
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + command.roleId()));

        if (groupRoleRepository.existsByLdapGroupAndRoleIdAndApplicationId(
                command.ldapGroup(), command.roleId(), command.applicationId())) {
            log.debug("Group '{}' is already assigned to role '{}' — skipping",
                command.ldapGroup(), command.roleId());
            return groupRoleRepository.findByApplicationId(command.applicationId()).stream()
                .filter(a -> a.ldapGroup().equals(command.ldapGroup())
                    && a.roleId().equals(command.roleId()))
                .findFirst()
                .orElseThrow();
        }

        GroupRoleAssignment assignment = new GroupRoleAssignment(
            UUID.randomUUID().toString(),
            command.ldapGroup(),
            command.roleId(),
            command.applicationId(),
            command.assignedBy(),
            Instant.now()
        );

        GroupRoleAssignment saved = groupRoleRepository.save(assignment);

        auditEventPort.publish(AuditEvent.authorization(
            "GROUP_ROLE_ASSIGNED",
            command.assignedBy(),
            command.ldapGroup(),
            command.applicationId(),
            "group-role",
            "assign",
            "SUCCESS",
            Map.of("roleId", command.roleId(), "ldapGroup", command.ldapGroup())
        ));

        log.info("LDAP group '{}' assigned to role '{}' in application '{}' by '{}'",
            command.ldapGroup(), command.roleId(), command.applicationId(), command.assignedBy());

        return saved;
    }

    @Override
    public void revokeGroupFromRole(String ldapGroup, String roleId, String applicationId) {
        groupRoleRepository.delete(ldapGroup, roleId, applicationId);

        auditEventPort.publish(AuditEvent.authorization(
            "GROUP_ROLE_REVOKED",
            "admin-api",
            ldapGroup,
            applicationId,
            "group-role",
            "revoke",
            "SUCCESS",
            Map.of("roleId", roleId, "ldapGroup", ldapGroup)
        ));

        log.info("LDAP group '{}' revoked from role '{}' in application '{}'",
            ldapGroup, roleId, applicationId);
    }

    @Override
    public List<GroupRoleAssignment> getGroupAssignmentsForApplication(String applicationId) {
        return groupRoleRepository.findByApplicationId(applicationId);
    }
}
