package com.authplatform.auth.domain.model;

import java.time.Instant;
import java.util.List;

public record Token(
    String tokenId,
    String username,
    TokenType type,
    Instant issuedAt,
    Instant expiresAt,
    List<String> roles,
    List<String> groups,
    String applicationId,
    String clientIp
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isAccessToken() {
        return TokenType.ACCESS.equals(type);
    }

    public boolean isRefreshToken() {
        return TokenType.REFRESH.equals(type);
    }
}
