package com.authplatform.authorization.infrastructure.persistence.adapter;

import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.port.out.PermissionRepository;
import com.authplatform.authorization.infrastructure.persistence.entity.PermissionEntity;
import com.authplatform.authorization.infrastructure.persistence.repository.PermissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PermissionRepositoryAdapter implements PermissionRepository {

    private final PermissionJpaRepository jpaRepository;

    @Override
    public Permission save(Permission permission) {
        return toDomain(jpaRepository.save(toEntity(permission)));
    }

    @Override
    public Optional<Permission> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Permission> findByResourceAndAction(String resource, String action) {
        return jpaRepository.findByResourceAndAction(resource, action).map(this::toDomain);
    }

    @Override
    public List<Permission> findAll() {
        return jpaRepository.findAll().stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private PermissionEntity toEntity(Permission permission) {
        return PermissionEntity.builder()
            .id(permission.id())
            .name(permission.name())
            .description(permission.description())
            .resource(permission.resource())
            .action(permission.action())
            .build();
    }

    private Permission toDomain(PermissionEntity entity) {
        return new Permission(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getResource(),
            entity.getAction()
        );
    }
}
