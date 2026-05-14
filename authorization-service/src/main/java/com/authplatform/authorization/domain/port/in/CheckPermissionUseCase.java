package com.authplatform.authorization.domain.port.in;

import java.util.List;

public interface CheckPermissionUseCase {

    /**
     * Checks whether any of the given LDAP groups has permission to perform
     * the specified action on the resource within the application.
     *
     * @param ldapGroups raw group names extracted from the JWT "groups" claim
     * @param ldapRoles  ROLE_-prefixed role names from the JWT "roles" claim,
     *                   used to enforce the application's allowedRoles gate
     */
    boolean hasPermission(String username, List<String> ldapGroups, List<String> ldapRoles,
                          String applicationId, String resource, String action);

    /** Returns the distinct set of permission scopes (resource:action) for the given LDAP groups. */
    List<String> getUserPermissions(String username, List<String> ldapGroups, List<String> ldapRoles,
                                    String applicationId);

    /** Returns the Role names that the given LDAP groups are mapped to in the application. */
    List<String> getUserRoles(List<String> ldapGroups, String applicationId);
}
