package com.authplatform.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Token introspection response (RFC 7662)")
public record TokenIntrospectResponse(
    @Schema(description = "Token is active/valid")
    boolean active,

    @Schema(description = "Token subject (username)")
    String sub,

    @Schema(description = "Token issuer")
    String iss,

    @Schema(description = "Token expiration (epoch seconds)")
    long exp,

    @Schema(description = "Token issued at (epoch seconds)")
    long iat,

    @Schema(description = "Token type")
    @JsonProperty("token_type")
    String tokenType,

    @Schema(description = "User roles")
    List<String> roles,

    @Schema(description = "User groups")
    List<String> groups,

    @Schema(description = "Application identifier")
    @JsonProperty("application_id")
    String applicationId
) {
    public static TokenIntrospectResponse inactive() {
        return new TokenIntrospectResponse(false, null, null, 0, 0, null, null, null, null);
    }
}
