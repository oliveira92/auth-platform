package com.authplatform.authorization.domain.port.out;

import com.authplatform.authorization.domain.model.Permission;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository {
    Permission save(Permission permission);
    Optional<Permission> findById(String id);
    Optional<Permission> findByResourceAndAction(String resource, String action);
    List<Permission> findAll();
    void delete(String id);
}
