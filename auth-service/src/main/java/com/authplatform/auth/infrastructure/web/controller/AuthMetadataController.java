package com.authplatform.auth.infrastructure.web.controller;

import com.authplatform.auth.infrastructure.config.JwtProperties;
import com.authplatform.auth.infrastructure.jwt.JwtKeyProvider;
import com.authplatform.auth.infrastructure.web.dto.JsonWebKeyResponse;
import com.authplatform.auth.infrastructure.web.dto.JsonWebKeySetResponse;
import com.authplatform.auth.infrastructure.web.dto.OpenIdConfigurationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth Metadata", description = "Public metadata for local JWT validation")
public class AuthMetadataController {

    private final JwtKeyProvider jwtKeyProvider;
    private final JwtProperties jwtProperties;

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Expose the active RSA public key in JWKS format")
    public ResponseEntity<JsonWebKeySetResponse> jwks() {
        JsonWebKeyResponse jwk = new JsonWebKeyResponse(
            "RSA",
            "sig",
            "RS256",
            jwtKeyProvider.getKeyId(),
            jwtKeyProvider.getModulus(),
            jwtKeyProvider.getExponent()
        );
        return ResponseEntity.ok(new JsonWebKeySetResponse(List.of(jwk)));
    }

    @GetMapping("/.well-known/openid-configuration")
    @Operation(summary = "Expose OpenID Connect discovery metadata for JWKS consumers")
    public ResponseEntity<OpenIdConfigurationResponse> openIdConfiguration() {
        String issuer = jwtProperties.getIssuer();
        return ResponseEntity.ok(new OpenIdConfigurationResponse(
            issuer,
            issuer.replaceAll("/+$", "") + "/.well-known/jwks.json",
            List.of("RS256"),
            List.of("token"),
            List.of("public"),
            List.of("sub", "iss", "aud", "exp", "iat", "jti", "roles", "groups", "applicationId", "ldapDomain", "type")
        ));
    }
}
