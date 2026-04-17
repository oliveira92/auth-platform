package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.in.CheckPermissionUseCase;
import com.authplatform.authorization.domain.port.out.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckPermissionUseCaseImpl implements CheckPermissionUseCase {

    private final RoleRepository roleRepository;

    @Override
    public boolean hasPermission(String username, String applicationId, String resource, String action) {
        List<Role> roles = roleRepository.findByUsernameAndApplicationId(username, applicationId);
        return roles.stream()
            .flatMap(role -> role.permissions().stream())
            .anyMatch(p -> p.resource().equals(resource) && p.action().equals(action));
    }

    @Override
    public List<String> getUserPermissions(String username, String applicationId) {
        List<Role> roles = roleRepository.findByUsernameAndApplicationId(username, applicationId);
        return roles.stream()
            .flatMap(role -> role.permissions().stream())
            .map(Permission::toScope)
            .distinct()
            .toList();
    }

    @Override
    public List<String> getUserRoles(String username, String applicationId) {
        return roleRepository.findByUsernameAndApplicationId(username, applicationId)
            .stream()
            .map(Role::name)
            .toList();
    }
}
