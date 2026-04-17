package com.authplatform.authorization.application.usecase;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.model.ApplicationStatus;
import com.authplatform.authorization.domain.port.in.RegisterApplicationCommand;
import com.authplatform.authorization.domain.port.in.RegisterApplicationUseCase;
import com.authplatform.authorization.domain.port.out.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterApplicationUseCaseImpl implements RegisterApplicationUseCase {

    private final ApplicationRepository applicationRepository;

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

        return applicationRepository.save(updated);
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
        log.info("Application deactivated: {}", applicationId);
    }

    private String generateClientId(String appName) {
        return appName.toLowerCase().replaceAll("[^a-z0-9]", "-") + "-" +
            UUID.randomUUID().toString().substring(0, 8);
    }
}
