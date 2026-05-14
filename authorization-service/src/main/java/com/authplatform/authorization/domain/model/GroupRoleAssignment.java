package com.authplatform.authorization.domain.model;

import java.time.Instant;

/**
 * Represents the mapping between an LDAP/AD group and a Role within an Application.
 *
 * <p>This is the central entity of Model 1 (LDAP as source of truth). Instead of
 * assigning roles to individual users, roles are assigned to LDAP groups. Any user
 * whose JWT contains a matching group automatically inherits those roles and their
 * associated permissions — no manual per-user assignment required.
 *
 * <p>Example: mapping the AD group "engineers" to the role "XPTO_USER" in "portal-xpto"
 * means every engineer who logs in automatically gets the XPTO_USER permissions.
 */
public record GroupRoleAssignment(
    String id,
    String ldapGroup,
    String roleId,
    String applicationId,
    String assignedBy,
    Instant assignedAt
) {}
