package com.authplatform.authorization.infrastructure.web.controller;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.port.in.RegisterApplicationCommand;
import com.authplatform.authorization.domain.port.in.RegisterApplicationUseCase;
import com.authplatform.authorization.infrastructure.config.AuthPlatformMetadataProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Application Management", description = "Self-service application registration")
public class ApplicationController {

    private final RegisterApplicationUseCase registerApplicationUseCase;
    private final AuthPlatformMetadataProperties authPlatformMetadataProperties;

    @PostMapping
    @Operation(summary = "Register a new application for authentication/authorization")
    public ResponseEntity<ApplicationResponse> registerApplication(
            @Valid @RequestBody RegisterApplicationRequest request) {

        Application app = registerApplicationUseCase.register(
            new RegisterApplicationCommand(
                request.name(),
                request.description(),
                request.ownerTeam(),
                request.allowedRoles()
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApplicationResponse.from(app, authPlatformMetadataProperties));
    }

    @PutMapping("/{applicationId}")
    @Operation(summary = "Update an existing application")
    public ResponseEntity<ApplicationResponse> updateApplication(
            @PathVariable String applicationId,
            @Valid @RequestBody RegisterApplicationRequest request) {

        Application app = registerApplicationUseCase.update(
            applicationId,
            new RegisterApplicationCommand(
                request.name(),
                request.description(),
                request.ownerTeam(),
                request.allowedRoles()
            )
        );
        return ResponseEntity.ok(ApplicationResponse.from(app, authPlatformMetadataProperties));
    }

    @DeleteMapping("/{applicationId}")
    @Operation(summary = "Deactivate an application")
    public ResponseEntity<Void> deactivateApplication(@PathVariable String applicationId) {
        registerApplicationUseCase.deactivate(applicationId);
        return ResponseEntity.noContent().build();
    }

    public record RegisterApplicationRequest(
        String name,
        String description,
        String ownerTeam,
        List<String> allowedRoles
    ) {}
}
