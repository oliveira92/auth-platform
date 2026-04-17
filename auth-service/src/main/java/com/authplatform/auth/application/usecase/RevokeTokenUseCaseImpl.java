package com.authplatform.auth.application.usecase;

import com.authplatform.auth.domain.exception.InvalidTokenException;
import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.port.in.RevokeTokenUseCase;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeTokenUseCaseImpl implements RevokeTokenUseCase {

    private final JwtPort jwtPort;
    private final TokenStoragePort tokenStoragePort;

    @Override
    public void revoke(String rawToken) {
        try {
            Token token = jwtPort.parseToken(rawToken);
            tokenStoragePort.revoke(token.tokenId());
            log.info("Token revoked for user: {}", token.username());
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot revoke invalid token", e);
        }
    }

    @Override
    public void revokeAllForUser(String username) {
        tokenStoragePort.revokeAllForUser(username);
        log.info("All tokens revoked for user: {}", username);
    }
}
