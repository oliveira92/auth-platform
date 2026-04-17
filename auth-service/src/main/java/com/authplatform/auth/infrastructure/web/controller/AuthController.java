package com.authplatform.auth.infrastructure.web.controller;

import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenPair;
import com.authplatform.auth.domain.port.in.AuthenticateUserCommand;
import com.authplatform.auth.domain.port.in.AuthenticateUserUseCase;
import com.authplatform.auth.domain.port.in.RefreshTokenCommand;
import com.authplatform.auth.domain.port.in.RefreshTokenUseCase;
import com.authplatform.auth.domain.port.in.RevokeTokenUseCase;
import com.authplatform.auth.domain.port.in.ValidateTokenUseCase;
import com.authplatform.auth.infrastructure.web.dto.LoginRequest;
import com.authplatform.auth.infrastructure.web.dto.LoginResponse;
import com.authplatform.auth.infrastructure.web.dto.RefreshTokenRequest;
import com.authplatform.auth.infrastructure.web.dto.TokenIntrospectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Corporate authentication endpoints")
public class AuthController {

    private final AuthenticateUserUseCase authenticateUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final RevokeTokenUseCase revokeTokenUseCase;

    @PostMapping("/login")
    @Operation(summary = "Authenticate user via LDAP/AD and issue JWT tokens")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        TokenPair tokenPair = authenticateUserUseCase.authenticate(
            AuthenticateUserCommand.of(
                request.username(),
                request.password(),
                request.applicationId(),
                getClientIp(httpRequest)
            )
        );

        return ResponseEntity.ok(new LoginResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.tokenType(),
            tokenPair.accessTokenExpiresIn(),
            tokenPair.refreshTokenExpiresIn()
        ));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        TokenPair tokenPair = refreshTokenUseCase.refresh(
            new RefreshTokenCommand(
                request.refreshToken(),
                request.applicationId(),
                getClientIp(httpRequest)
            )
        );

        return ResponseEntity.ok(new LoginResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.tokenType(),
            tokenPair.accessTokenExpiresIn(),
            tokenPair.refreshTokenExpiresIn()
        ));
    }

    @PostMapping("/validate")
    @Operation(summary = "Introspect/validate a token (RFC 7662 compatible)")
    public ResponseEntity<TokenIntrospectResponse> validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String rawToken = extractToken(authHeader);
        if (rawToken == null) {
            return ResponseEntity.ok(TokenIntrospectResponse.inactive());
        }

        Optional<Token> token = validateTokenUseCase.validate(rawToken);
        if (token.isEmpty()) {
            return ResponseEntity.ok(TokenIntrospectResponse.inactive());
        }

        Token t = token.get();
        return ResponseEntity.ok(new TokenIntrospectResponse(
            true,
            t.username(),
            "auth-platform",
            t.expiresAt().getEpochSecond(),
            t.issuedAt().getEpochSecond(),
            t.type().name(),
            t.roles(),
            t.groups(),
            t.applicationId()
        ));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "Revoke current token (logout)")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String rawToken = extractToken(authHeader);
        if (rawToken != null) {
            revokeTokenUseCase.revoke(rawToken);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/logout/all")
    @Operation(summary = "Revoke all tokens for a user (logout from all sessions)")
    public ResponseEntity<Void> logoutAll(
            @RequestHeader("X-Username") String username) {
        revokeTokenUseCase.revokeAllForUser(username);
        return ResponseEntity.noContent().build();
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
