package com.authplatform.auth.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login request payload")
public record LoginRequest(
    @NotBlank(message = "Username is required")
    @Schema(description = "LDAP username (sAMAccountName)", example = "john.doe")
    String username,

    @NotBlank(message = "Password is required")
    @Schema(description = "LDAP password", example = "SecurePass123!")
    String password,

    @Schema(description = "Client application identifier", example = "my-application")
    String applicationId
) {}
