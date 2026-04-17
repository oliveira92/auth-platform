package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.UserRoleAssignment;

import java.util.List;

public interface UserRoleRepository {
    UserRoleAssignment save(UserRoleAssignment assignment);
    List<UserRoleAssignment> findByUsernameAndApplicationId(String username, String applicationId);
    void delete(String username, String roleId, String applicationId);
    void deleteAllForUser(String username);
}
