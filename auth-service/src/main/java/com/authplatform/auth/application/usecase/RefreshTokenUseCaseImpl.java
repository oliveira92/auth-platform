package com.authplatform.auth.application.usecase;

import com.authplatform.auth.domain.exception.InvalidTokenException;
import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenPair;
import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.domain.port.in.RefreshTokenCommand;
import com.authplatform.auth.domain.port.in.RefreshTokenUseCase;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.domain.port.out.LdapUserPort;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
import com.authplatform.auth.domain.service.AuthenticationDomainService;
import com.authplatform.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

    private final JwtPort jwtPort;
    private final LdapUserPort ldapUserPort;
    private final TokenStoragePort tokenStoragePort;
    private final AuthenticationDomainService authenticationDomainService;
    private final JwtProperties jwtProperties;

    @Override
    public TokenPair refresh(RefreshTokenCommand command) {
        Token parsedRefreshToken = jwtPort.parseToken(command.refreshToken());

        if (!parsedRefreshToken.isRefreshToken()) {
            throw new InvalidTokenException("Provided token is not a refresh token");
        }

        if (parsedRefreshToken.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        if (tokenStoragePort.isRevoked(parsedRefreshToken.tokenId())) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        User user = ldapUserPort.findByUsername(parsedRefreshToken.username())
            .orElseThrow(() -> new InvalidTokenException("User not found for refresh token"));

        // Revoke old refresh token (rotation strategy)
        tokenStoragePort.revoke(parsedRefreshToken.tokenId());

        Token newAccessToken = authenticationDomainService.createAccessTokenFromRefresh(
            parsedRefreshToken, user, jwtProperties.getAccessTokenExpirationSeconds()
        );

        Token newRefreshToken = authenticationDomainService.createRefreshToken(
            user,
            parsedRefreshToken.applicationId(),
            command.clientIp(),
            jwtProperties.getRefreshTokenExpirationSeconds()
        );

        tokenStoragePort.save(newAccessToken, Duration.ofSeconds(jwtProperties.getAccessTokenExpirationSeconds()));
        tokenStoragePort.save(newRefreshToken, Duration.ofSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));

        log.info("Token refreshed for user: {}", user.username());
        return TokenPair.of(
            jwtPort.generateToken(newAccessToken),
            jwtPort.generateToken(newRefreshToken),
            jwtProperties.getAccessTokenExpirationSeconds(),
            jwtProperties.getRefreshTokenExpirationSeconds()
        );
    }
}
