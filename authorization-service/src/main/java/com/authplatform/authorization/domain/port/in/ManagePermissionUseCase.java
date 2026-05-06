package com.authplatform.authorization.domain.port.in;

import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;

import java.util.List;

public interface ManagePermissionUseCase {
    Permission createPermission(CreatePermissionCommand command);
    Permission updatePermission(String permissionId, CreatePermissionCommand command);
    Permission getPermission(String permissionId);
    List<Permission> listPermissions();
    void deletePermission(String permissionId);
    Role assignPermissionToRole(String roleId, String permissionId);
    Role revokePermissionFromRole(String roleId, String permissionId);
}
