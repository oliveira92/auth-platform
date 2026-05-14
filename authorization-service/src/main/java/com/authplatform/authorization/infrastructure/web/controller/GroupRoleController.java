package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.GroupRoleAssignment;
import com.authplatform.authorization.domain.port.in.AssignGroupRoleCommand;
import com.authplatform.authorization.domain.port.in.ManageGroupRoleUseCase;
import com.authplatform.authorization.infrastructure.web.security.AuthPlatformPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Group Role Management",
     description = "Maps AD/LDAP groups to Roles (Model 1 — LDAP as source of truth)")
public class GroupRoleController {

    private final ManageGroupRoleUseCase manageGroupRoleUseCase;

    @PostMapping("/{roleId}/group-assignments")
    @Operation(
        summary = "Assign an LDAP group to a Role",
        description = "Maps an AD/LDAP group name (as it appears in the JWT 'groups' claim) " +
                      "to a Role in the given Application. All users whose JWT contains this " +
                      "group will automatically inherit the Role's permissions."
    )
    public ResponseEntity<GroupRoleAssignment> assignGroupToRole(
            @PathVariable String roleId,
            @Valid @RequestBody AssignGroupRequest request,
            Authentication authentication) {

        String assignedBy = resolveActor(authentication);
        GroupRoleAssignment assignment = manageGroupRoleUseCase.assignGroupToRole(
            new AssignGroupRoleCommand(
                request.ldapGroup(),
                roleId,
                request.applicationId(),
                assignedBy
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }

    @DeleteMapping("/{roleId}/group-assignments")
    @Operation(
        summary = "Revoke an LDAP group from a Role",
        description = "Removes the mapping between an AD/LDAP group and a Role. " +
                      "Users in that group will immediately lose the Role's permissions on the next request."
    )
    public ResponseEntity<Void> revokeGroupFromRole(
            @PathVariable String roleId,
            @RequestParam @NotBlank String ldapGroup,
            @RequestParam @NotBlank String applicationId) {

        manageGroupRoleUseCase.revokeGroupFromRole(ldapGroup, roleId, applicationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/group-assignments")
    @Operation(
        summary = "List all LDAP group → Role mappings for an Application",
        description = "Returns all configured group-to-role assignments. " +
                      "Useful to audit which AD groups grant which roles."
    )
    public ResponseEntity<List<GroupRoleAssignment>> listGroupAssignments(
            @RequestParam @NotBlank String applicationId) {

        return ResponseEntity.ok(
            manageGroupRoleUseCase.getGroupAssignmentsForApplication(applicationId)
        );
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null) return "system";
        if (authentication.getPrincipal() instanceof AuthPlatformPrincipal p) return p.username();
        return authentication.getName();
    }

    public record AssignGroupRequest(
        @NotBlank String ldapGroup,
        @NotBlank String applicationId
    ) {}
}
