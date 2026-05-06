package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.AuditEvent;
import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.model.ApplicationStatus;
import com.authplatform.authorization.domain.port.in.RegisterApplicationCommand;
import com.authplatform.authorization.domain.port.in.RegisterApplicationUseCase;
import com.authplatform.authorization.domain.port.out.AuditEventPort;
import com.authplatform.authorization.domain.port.out.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterApplicationUseCaseImpl implements RegisterApplicationUseCase {

    private final ApplicationRepository applicationRepository;
    private final AuditEventPort auditEventPort;

    @Override
    public Application register(RegisterApplicationCommand command) {
        String appId = UUID.randomUUID().toString();
        String clientId = generateClientId(command.name());

        Application application = new Application(
            appId, command.name(), command.description(),
            clientId, ApplicationStatus.ACTIVE,
            command.ownerTeam(), command.allowedRoles(),
            Instant.now(), Instant.now()
        );

        Application saved = applicationRepository.save(application);
        auditEventPort.publish(AuditEvent.authorization(
            "APPLICATION_REGISTERED",
            command.ownerTeam(),
            saved.id(),
            saved.clientId(),
            "application",
            "register",
            "SUCCESS",
            Map.of("name", saved.name(), "ownerTeam", String.valueOf(saved.ownerTeam()))
        ));
        log.info("Application registered: {} (clientId: {})", saved.name(), saved.clientId());
        return saved;
    }

    @Override
    public Application update(String applicationId, RegisterApplicationCommand command) {
        Application existing = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        Application updated = new Application(
            existing.id(), command.name(), command.description(),
            existing.clientId(), existing.status(),
            command.ownerTeam(), command.allowedRoles(),
            existing.createdAt(), Instant.now()
        );

        Application saved = applicationRepository.save(updated);
        auditEventPort.publish(AuditEvent.authorization(
            "APPLICATION_UPDATED",
            command.ownerTeam(),
            saved.id(),
            saved.clientId(),
            "application",
            "update",
            "SUCCESS",
            Map.of("name", saved.name(), "ownerTeam", String.valueOf(saved.ownerTeam()))
        ));
        return saved;
    }

    @Override
    public void deactivate(String applicationId) {
        Application existing = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        Application deactivated = new Application(
            existing.id(), existing.name(), existing.description(),
            existing.clientId(), ApplicationStatus.INACTIVE,
            existing.ownerTeam(), existing.allowedRoles(),
            existing.createdAt(), Instant.now()
        );

        applicationRepository.save(deactivated);
        auditEventPort.publish(AuditEvent.authorization(
            "APPLICATION_DEACTIVATED",
            existing.ownerTeam(),
            existing.id(),
            existing.clientId(),
            "application",
            "deactivate",
            "SUCCESS",
            Map.of("name", existing.name())
        ));
        log.info("Application deactivated: {}", applicationId);
    }

    private String generateClientId(String appName) {
        return appName.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" +
            UUID.randomUUID().toString().substring(0, 8);
    }
}
