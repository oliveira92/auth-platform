package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.in.CreateRoleCommand;
import com.authplatform.authorization.domain.port.in.ManageRoleUseCase;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "RBAC role and assignment management")
public class RoleController {

    private final ManageRoleUseCase manageRoleUseCase;

    @PostMapping
    @Operation(summary = "Create a new role for an application")
    public ResponseEntity<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        Role role = manageRoleUseCase.createRole(
            new CreateRoleCommand(request.name(), request.description(), request.applicationId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @GetMapping
    @Operation(summary = "List all roles for an application")
    public ResponseEntity<List<Role>> listRoles(@RequestParam String applicationId) {
        return ResponseEntity.ok(manageRoleUseCase.getRolesForApplication(applicationId));
    }

    @PostMapping("/{roleId}/assignments")
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<Void> assignRole(
            @PathVariable String roleId,
            @Valid @RequestBody AssignRoleRequest request,
            @RequestHeader(value = "X-Username", required = false) String requestingUser) {

        String assignedBy = requestingUser != null ? requestingUser : "system";
        manageRoleUseCase.assignRoleToUser(request.username(), roleId, request.applicationId(), assignedBy);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{roleId}/assignments")
    @Operation(summary = "Revoke a role from a user")
    public ResponseEntity<Void> revokeRole(
            @PathVariable String roleId,
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String applicationId) {

        manageRoleUseCase.revokeRoleFromUser(username, roleId, applicationId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRoleRequest(
        @NotBlank String name,
        String description,
        @NotBlank String applicationId
    ) {}

    public record AssignRoleRequest(
        @NotBlank String username,
        @NotBlank String applicationId
    ) {}
}
