package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.in.CreatePermissionCommand;
import com.authplatform.authorization.domain.port.in.ManagePermissionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "RBAC permission and role-permission management")
public class PermissionController {

    private final ManagePermissionUseCase managePermissionUseCase;

    @PostMapping("/permissions")
    @Operation(summary = "Create a permission")
    public ResponseEntity<Permission> createPermission(@Valid @RequestBody PermissionRequest request) {
        Permission permission = managePermissionUseCase.createPermission(toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
    }

    @GetMapping("/permissions")
    @Operation(summary = "List permissions")
    public ResponseEntity<List<Permission>> listPermissions() {
        return ResponseEntity.ok(managePermissionUseCase.listPermissions());
    }

    @GetMapping("/permissions/{permissionId}")
    @Operation(summary = "Get a permission")
    public ResponseEntity<Permission> getPermission(@PathVariable String permissionId) {
        return ResponseEntity.ok(managePermissionUseCase.getPermission(permissionId));
    }

    @PutMapping("/permissions/{permissionId}")
    @Operation(summary = "Update a permission")
    public ResponseEntity<Permission> updatePermission(
            @PathVariable String permissionId,
            @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(managePermissionUseCase.updatePermission(permissionId, toCommand(request)));
    }

    @DeleteMapping("/permissions/{permissionId}")
    @Operation(summary = "Delete a permission")
    public ResponseEntity<Void> deletePermission(@PathVariable String permissionId) {
        managePermissionUseCase.deletePermission(permissionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Assign a permission to a role")
    public ResponseEntity<Role> assignPermissionToRole(
            @PathVariable String roleId,
            @PathVariable String permissionId) {
        return ResponseEntity.ok(managePermissionUseCase.assignPermissionToRole(roleId, permissionId));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke a permission from a role")
    public ResponseEntity<Role> revokePermissionFromRole(
            @PathVariable String roleId,
            @PathVariable String permissionId) {
        return ResponseEntity.ok(managePermissionUseCase.revokePermissionFromRole(roleId, permissionId));
    }

    private CreatePermissionCommand toCommand(PermissionRequest request) {
        return new CreatePermissionCommand(
            request.name(),
            request.description(),
            request.resource(),
            request.action()
        );
    }

    public record PermissionRequest(
        @NotBlank String name,
        String description,
        @NotBlank String resource,
        @NotBlank String action
    ) {}
}
