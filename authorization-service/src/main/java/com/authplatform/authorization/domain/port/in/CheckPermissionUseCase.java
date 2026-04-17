package com.authplatform.authorization.domain.port.in;

import java.util.List;

public interface CheckPermissionUseCase {
    boolean hasPermission(String username, String applicationId, String resource, String action);
    List<String> getUserPermissions(String username, String applicationId);
    List<String> getUserRoles(String username, String applicationId);
}
