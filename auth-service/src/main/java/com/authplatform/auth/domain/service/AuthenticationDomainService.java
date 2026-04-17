package com.authplatform.auth.domain.service;

import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenType;
import com.authplatform.auth.domain.model.User;

import java.time.Instant;
import java.util.UUID;

public class AuthenticationDomainService {

    public Token createAccessToken(User user, String applicationId, String clientIp,
                                    long expirationSeconds) {
        return new Token(
            UUID.randomUUID().toString(),
            user.username(),
            TokenType.ACCESS,
            Instant.now(),
            Instant.now().plusSeconds(expirationSeconds),
            user.roles(),
            user.groups(),
            applicationId,
            clientIp
        );
    }

    public Token createRefreshToken(User user, String applicationId, String clientIp,
                                     long expirationSeconds) {
        return new Token(
            UUID.randomUUID().toString(),
            user.username(),
            TokenType.REFRESH,
            Instant.now(),
            Instant.now().plusSeconds(expirationSeconds),
            user.roles(),
            user.groups(),
            applicationId,
            clientIp
        );
    }

    public Token createAccessTokenFromRefresh(Token refreshToken, User user,
                                               long expirationSeconds) {
        return new Token(
            UUID.randomUUID().toString(),
            refreshToken.username(),
            TokenType.ACCESS,
            Instant.now(),
            Instant.now().plusSeconds(expirationSeconds),
            user.roles(),
            user.groups(),
            refreshToken.applicationId(),
            refreshToken.clientIp()
        );
    }
}
