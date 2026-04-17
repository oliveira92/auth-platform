package com.authplatform.auth.application.usecase;

import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.port.in.ValidateTokenUseCase;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateTokenUseCaseImpl implements ValidateTokenUseCase {

    private final JwtPort jwtPort;
    private final TokenStoragePort tokenStoragePort;

    @Override
    public Optional<Token> validate(String rawToken) {
        try {
            Token token = jwtPort.parseToken(rawToken);

            if (token.isExpired()) {
                log.debug("Token expired for user: {}", token.username());
                return Optional.empty();
            }

            if (tokenStoragePort.isRevoked(token.tokenId())) {
                log.warn("Revoked token used by user: {}", token.username());
                return Optional.empty();
            }

            return Optional.of(token);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
