package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {
    Role save(Role role);
    Optional<Role> findById(String id);
    List<Role> findByApplicationId(String applicationId);
    List<Role> findByUsernameAndApplicationId(String username, String applicationId);

    /**
     * Finds all Roles mapped to any of the given LDAP groups within an Application.
     * This is the core query for Model 1 (LDAP as source of truth).
     *
     * @param ldapGroups raw LDAP group names from the JWT "groups" claim
     */
    List<Role> findByLdapGroupsAndApplicationId(List<String> ldapGroups, String applicationId);
    Role assignPermission(String roleId, String permissionId);
    Role revokePermission(String roleId, String permissionId);
    void delete(String id);
}
