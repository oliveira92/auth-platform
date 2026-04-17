package com.authplatform.authorization.domain.port.in;

import com.authplatform.authorization.domain.model.Role;

import java.util.List;

public interface ManageRoleUseCase {
    Role createRole(CreateRoleCommand command);
    void assignRoleToUser(String username, String roleId, String applicationId, String assignedBy);
    void revokeRoleFromUser(String username, String roleId, String applicationId);
    List<Role> getRolesForApplication(String applicationId);
}
