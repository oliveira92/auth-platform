package com.authplatform.auth.application.usecase;

import com.authplatform.auth.domain.model.AuditEvent;
import com.authplatform.auth.domain.model.Token;
import com.authplatform.auth.domain.model.TokenPair;
import com.authplatform.auth.domain.model.User;
import com.authplatform.auth.domain.port.in.AuthenticateUserCommand;
import com.authplatform.auth.domain.port.in.AuthenticateUserUseCase;
import com.authplatform.auth.domain.port.out.AuditEventPort;
import com.authplatform.auth.domain.port.out.JwtPort;
import com.authplatform.auth.domain.port.out.LdapUserPort;
import com.authplatform.auth.domain.port.out.TokenStoragePort;
import com.authplatform.auth.domain.service.AuthenticationDomainService;
import com.authplatform.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

    private final LdapUserPort ldapUserPort;
    private final JwtPort jwtPort;
    private final TokenStoragePort tokenStoragePort;
    private final AuditEventPort auditEventPort;
    private final AuthenticationDomainService authenticationDomainService;
    private final JwtProperties jwtProperties;

    @Override
    public TokenPair authenticate(AuthenticateUserCommand command) {
        log.info("Authenticating user: {} from application: {}", command.username(), command.applicationId());

        User user;
        try {
            user = ldapUserPort.authenticate(command.username(), command.password(), command.ldapDomain());
        } catch (RuntimeException ex) {
            auditEventPort.publish(AuditEvent.auth(
                "AUTH_LOGIN",
                command.username(),
                command.username(),
                command.applicationId(),
                "FAILURE",
                command.clientIp(),
                Map.of("reason", ex.getClass().getSimpleName())
            ));
            throw ex;
        }

        Token accessToken = authenticationDomainService.createAccessToken(
            user,
            command.applicationId(),
            command.clientIp(),
            jwtProperties.getAccessTokenExpirationSeconds()
        );

        Token refreshToken = authenticationDomainService.createRefreshToken(
            user,
            command.applicationId(),
            command.clientIp(),
            jwtProperties.getRefreshTokenExpirationSeconds()
        );

        tokenStoragePort.save(accessToken, Duration.ofSeconds(jwtProperties.getAccessTokenExpirationSeconds()));
        tokenStoragePort.save(refreshToken, Duration.ofSeconds(jwtProperties.getRefreshTokenExpirationSeconds()));

        String rawAccessToken = jwtPort.generateToken(accessToken);
        String rawRefreshToken = jwtPort.generateToken(refreshToken);

        auditEventPort.publish(AuditEvent.auth(
            "AUTH_LOGIN",
            user.username(),
            user.username(),
            command.applicationId(),
            "SUCCESS",
            command.clientIp(),
            Map.of("ldapDomain", user.ldapDomain())
        ));

        log.info("Authentication successful for user: {}", command.username());
        return TokenPair.of(rawAccessToken, rawRefreshToken,
            jwtProperties.getAccessTokenExpirationSeconds(),
            jwtProperties.getRefreshTokenExpirationSeconds());
    }
}
