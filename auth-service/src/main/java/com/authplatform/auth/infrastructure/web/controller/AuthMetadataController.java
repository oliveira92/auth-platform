package com.authplatform.auth.infrastructure.web.controller;

import com.authplatform.auth.infrastructure.jwt.JwtKeyProvider;
import com.authplatform.auth.infrastructure.web.dto.JsonWebKeyResponse;
import com.authplatform.auth.infrastructure.web.dto.JsonWebKeySetResponse;
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
}
