package com.authplatform.authorization.domain.port.in;

import com.authplatform.authorization.domain.model.GroupRoleAssignment;

import java.util.List;

/**
 * Use case for managing LDAP group → Role assignments (Model 1 — LDAP as source of truth).
 */
public interface ManageGroupRoleUseCase {

    /** Assigns an LDAP group to a Role. Idempotent: duplicate assignments are silently ignored. */
    GroupRoleAssignment assignGroupToRole(AssignGroupRoleCommand command);

    /** Removes the assignment between an LDAP group and a Role. */
    void revokeGroupFromRole(String ldapGroup, String roleId, String applicationId);

    /** Returns all group assignments for a given Application. */
    List<GroupRoleAssignment> getGroupAssignmentsForApplication(String applicationId);
}
