package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.in.CheckPermissionUseCase;
import com.authplatform.authorization.domain.port.out.ApplicationRepository;
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
    private final ApplicationRepository applicationRepository;

    @Override
    public boolean hasPermission(String username, List<String> ldapGroups, List<String> ldapRoles,
                                 String applicationId, String resource, String action) {
        if (!isAllowedForApplication(ldapRoles, applicationId)) {
            log.debug("User '{}' blocked by allowedRoles gate for application '{}'", username, applicationId);
            return false;
        }

        List<Role> roles = roleRepository.findByLdapGroupsAndApplicationId(ldapGroups, applicationId);
        return roles.stream()
            .flatMap(role -> role.permissions().stream())
            .anyMatch(p -> p.resource().equals(resource) && p.action().equals(action));
    }

    @Override
    public List<String> getUserPermissions(String username, List<String> ldapGroups, List<String> ldapRoles,
                                           String applicationId) {
        if (!isAllowedForApplication(ldapRoles, applicationId)) {
            log.debug("User '{}' blocked by allowedRoles gate for application '{}'", username, applicationId);
            return List.of();
        }

        List<Role> roles = roleRepository.findByLdapGroupsAndApplicationId(ldapGroups, applicationId);
        return roles.stream()
            .flatMap(role -> role.permissions().stream())
            .map(Permission::toScope)
            .distinct()
            .toList();
    }

    @Override
    public List<String> getUserRoles(List<String> ldapGroups, String applicationId) {
        return roleRepository.findByLdapGroupsAndApplicationId(ldapGroups, applicationId).stream()
            .map(Role::name)
            .toList();
    }

    /**
     * Enforces the Application's allowedRoles gate.
     *
     * <p>If the application has no allowedRoles configured, access is unrestricted.
     * Otherwise, at least one of the user's JWT roles (ROLE_-prefixed LDAP roles)
     * must match an entry in the application's allowedRoles list.
     *
     * <p>Example: application allowedRoles = ["ROLE_ENGINEERS", "ROLE_ADMINISTRATORS"]
     * User JWT roles = ["ROLE_ENGINEERS"] → allowed
     * User JWT roles = ["ROLE_CONTRACTORS"] → blocked
     */
    private boolean isAllowedForApplication(List<String> ldapRoles, String applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            log.warn("Application not found: {}", applicationId);
            return false;
        }

        List<String> allowedRoles = application.allowedRoles();
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        return ldapRoles != null && ldapRoles.stream().anyMatch(allowedRoles::contains);
    }
}
