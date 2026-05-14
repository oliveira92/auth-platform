package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.GroupRoleAssignment;

import java.util.List;

public interface GroupRoleRepository {
    GroupRoleAssignment save(GroupRoleAssignment assignment);
    boolean existsByLdapGroupAndRoleIdAndApplicationId(String ldapGroup, String roleId, String applicationId);
    void delete(String ldapGroup, String roleId, String applicationId);
    List<GroupRoleAssignment> findByApplicationId(String applicationId);
}
