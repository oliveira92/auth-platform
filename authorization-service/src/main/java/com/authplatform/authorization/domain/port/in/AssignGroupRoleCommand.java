package com.authplatform.authorization.domain.port.in;

/**
 * Command to assign an LDAP/AD group to a Role within an Application.
 *
 * @param ldapGroup     raw LDAP group name as it appears in the JWT "groups" claim (e.g. "engineers")
 * @param roleId        ID of the target Role in this authorization-service
 * @param applicationId ID of the Application that owns the Role
 * @param assignedBy    username of the admin performing this operation
 */
public record AssignGroupRoleCommand(
    String ldapGroup,
    String roleId,
    String applicationId,
    String assignedBy
) {}
