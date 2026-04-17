package com.authplatform.authorization.infrastructure.persistence.adapter;

import com.authplatform.authorization.domain.model.Application;
import com.authplatform.authorization.domain.port.out.ApplicationRepository;
import com.authplatform.authorization.infrastructure.persistence.entity.ApplicationEntity;
import com.authplatform.authorization.infrastructure.persistence.repository.ApplicationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApplicationRepositoryAdapter implements ApplicationRepository {

    private final ApplicationJpaRepository jpaRepository;

    @Override
    public Application save(Application app) {
        ApplicationEntity entity = toEntity(app);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Application> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Application> findByClientId(String clientId) {
        return jpaRepository.findByClientId(clientId).map(this::toDomain);
    }

    @Override
    public List<Application> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private ApplicationEntity toEntity(Application app) {
        return ApplicationEntity.builder()
            .id(app.id())
            .name(app.name())
            .description(app.description())
            .clientId(app.clientId())
            .status(app.status())
            .ownerTeam(app.ownerTeam())
            .allowedRoles(app.allowedRoles())
            .build();
    }

    private Application toDomain(ApplicationEntity entity) {
        return new Application(
            entity.getId(), entity.getName(), entity.getDescription(),
            entity.getClientId(), entity.getStatus(), entity.getOwnerTeam(),
            entity.getAllowedRoles(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
