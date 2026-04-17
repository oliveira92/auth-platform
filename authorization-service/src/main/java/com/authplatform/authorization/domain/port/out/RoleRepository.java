package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {
    Role save(Role role);
    Optional<Role> findById(String id);
    List<Role> findByApplicationId(String applicationId);
    List<Role> findByUsernameAndApplicationId(String username, String applicationId);
    void delete(String id);
}
