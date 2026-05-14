package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.port.in.CheckPermissionUseCase;
import com.authplatform.authorization.infrastructure.web.security.AuthPlatformPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/authorization")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "RBAC permission check endpoints (Model 1 — LDAP as source of truth)")
public class AuthorizationController {

    private final CheckPermissionUseCase checkPermissionUseCase;

    @GetMapping("/check")
    @Operation(summary = "Check if the authenticated user has a specific permission",
               description = "Permission is derived from the user's LDAP groups present in the JWT. " +
                             "No manual per-user assignment is required.")
    public ResponseEntity<Map<String, Object>> checkPermission(
            Authentication authentication,
            @RequestParam String applicationId,
            @RequestParam String resource,
            @RequestParam String action) {

        AuthPlatformPrincipal principal = extractPrincipal(authentication);
        boolean allowed = checkPermissionUseCase.hasPermission(
            principal.username(),
            principal.ldapGroups(),
            principal.ldapRoles(),
            applicationId, resource, action
        );

        return ResponseEntity.ok(Map.of(
            "allowed", allowed,
            "username", principal.username(),
            "applicationId", applicationId,
            "resource", resource,
            "action", action
        ));
    }

    @GetMapping("/permissions")
    @Operation(summary = "List all permissions for the authenticated user in an application",
               description = "Returns permissions derived from the user's LDAP groups in the JWT. " +
                             "Also returns the matched Role names for observability.")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            Authentication authentication,
            @RequestParam String applicationId) {

        AuthPlatformPrincipal principal = extractPrincipal(authentication);

        List<String> permissions = checkPermissionUseCase.getUserPermissions(
            principal.username(),
            principal.ldapGroups(),
            principal.ldapRoles(),
            applicationId
        );

        List<String> roles = checkPermissionUseCase.getUserRoles(
            principal.ldapGroups(),
            applicationId
        );

        return ResponseEntity.ok(Map.of(
            "username", principal.username(),
            "applicationId", applicationId,
            "ldapGroups", principal.ldapGroups(),
            "roles", roles,
            "permissions", permissions
        ));
    }

    private AuthPlatformPrincipal extractPrincipal(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthPlatformPrincipal principal) {
            return principal;
        }
        // Fallback: JWT without groups (e.g. emitted by older auth-service version)
        String username = authentication.getName();
        return AuthPlatformPrincipal.of(username, List.of(), List.of());
    }
}
