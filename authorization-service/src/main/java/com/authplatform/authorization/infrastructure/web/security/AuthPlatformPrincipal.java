package com.authplatform.authorization.infrastructure.web.security;

import java.util.List;

/**
 * Custom principal stored in the {@link org.springframework.security.core.Authentication}
 * after JWT validation.
 *
 * <p>Carries both the username and the LDAP groups/roles extracted from the JWT claims,
 * making them available throughout the request lifecycle without additional token parsing.
 *
 * @param username   JWT "sub" claim — the authenticated user's login name
 * @param ldapGroups raw LDAP group names from the JWT "groups" claim (e.g. ["engineers"])
 * @param ldapRoles  ROLE_-prefixed names from the JWT "roles" claim (e.g. ["ROLE_ENGINEERS"])
 *                   used to enforce the Application's allowedRoles gate
 */
public record AuthPlatformPrincipal(
    String username,
    List<String> ldapGroups,
    List<String> ldapRoles
) {
    public static AuthPlatformPrincipal of(String username, List<String> groups, List<String> roles) {
        return new AuthPlatformPrincipal(
            username,
            groups  != null ? groups : List.of(),
            roles   != null ? roles  : List.of()
        );
    }
}
