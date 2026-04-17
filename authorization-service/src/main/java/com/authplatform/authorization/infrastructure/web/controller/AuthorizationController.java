package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.port.in.CheckPermissionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/authorization")
@RequiredArgsConstructor
@Tag(name = "Authorization", description = "RBAC permission and role endpoints")
public class AuthorizationController {

    private final CheckPermissionUseCase checkPermissionUseCase;

    @GetMapping("/check")
    @Operation(summary = "Check if a user has a specific permission on a resource")
    public ResponseEntity<Map<String, Object>> checkPermission(
            @RequestHeader("X-Username") String username,
            @RequestParam String applicationId,
            @RequestParam String resource,
            @RequestParam String action) {

        boolean allowed = checkPermissionUseCase.hasPermission(username, applicationId, resource, action);

        return ResponseEntity.ok(Map.of(
            "allowed", allowed,
            "username", username,
            "applicationId", applicationId,
            "resource", resource,
            "action", action
        ));
    }

    @GetMapping("/permissions")
    @Operation(summary = "Get all permissions for a user in an application")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @RequestHeader("X-Username") String username,
            @RequestParam String applicationId) {

        List<String> permissions = checkPermissionUseCase.getUserPermissions(username, applicationId);
        List<String> roles = checkPermissionUseCase.getUserRoles(username, applicationId);

        return ResponseEntity.ok(Map.of(
            "username", username,
            "applicationId", applicationId,
            "roles", roles,
            "permissions", permissions
        ));
    }
}
