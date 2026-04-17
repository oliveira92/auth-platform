package com.authplatform.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful login response with JWT tokens")
public record LoginResponse(
    @JsonProperty("access_token")
    @Schema(description = "JWT access token")
    String accessToken,

    @JsonProperty("refresh_token")
    @Schema(description = "JWT refresh token")
    String refreshToken,

    @JsonProperty("token_type")
    @Schema(description = "Token type", example = "Bearer")
    String tokenType,

    @JsonProperty("expires_in")
    @Schema(description = "Access token expiration in seconds", example = "900")
    long expiresIn,

    @JsonProperty("refresh_expires_in")
    @Schema(description = "Refresh token expiration in seconds", example = "86400")
    long refreshExpiresIn
) {}
