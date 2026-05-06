package com.authplatform.auth.application.usecase;

import com.authplatform.auth.domain.model.AuditEvent;
import com.authplatform.auth.domain.exception.InvalidTokenException;
import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.port.out.AuditEventPort;
import com.authplatform.auth.domain.port.in.RevokeTokenUseCase;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeTokenUseCaseImpl implements RevokeTokenUseCase {

    private final JwtPort jwtPort;
    private final TokenStoragePort tokenStoragePort;
    private final AuditEventPort auditEventPort;

    @Override
    public void revoke(String rawToken) {
        try {
            Token token = jwtPort.parseToken(rawToken);
            tokenStoragePort.revoke(token.tokenId());
            auditEventPort.publish(AuditEvent.auth(
                "AUTH_LOGOUT",
                token.username(),
                token.username(),
                token.applicationId(),
                "SUCCESS",
                token.clientIp(),
                Map.of("ldapDomain", token.ldapDomain())
            ));
            log.info("Token revoked for user: {}", token.username());
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot revoke invalid token", e);
        }
    }

    @Override
    public void revokeAllForUser(String username) {
        tokenStoragePort.revokeAllForUser(username);
        auditEventPort.publish(AuditEvent.auth(
            "AUTH_LOGOUT_ALL",
            username,
            username,
            null,
            "SUCCESS",
            null,
            Map.of()
        ));
        log.info("All tokens revoked for user: {}", username);
    }
}
