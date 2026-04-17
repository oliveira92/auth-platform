package com.authplatform.authorization.infrastructure.persistence.adapter;

import com.authplatform.authorization.domain.model.Permission;
import com.authplatform.authorization.domain.model.Role;
import com.authplatform.authorization.domain.port.out.RoleRepository;
import com.authplatform.authorization.infrastructure.persistence.entity.PermissionEntity;
import com.authplatform.authorization.infrastructure.persistence.entity.RoleEntity;
import com.authplatform.authorization.infrastructure.persistence.repository.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;

    @Override
    public Role save(Role role) {
        RoleEntity entity = toEntity(role);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Role> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Role> findByApplicationId(String applicationId) {
        return jpaRepository.findByApplicationId(applicationId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Role> findByUsernameAndApplicationId(String username, String applicationId) {
        return jpaRepository.findByUsernameAndApplicationId(username, applicationId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private RoleEntity toEntity(Role role) {
        List<PermissionEntity> permissionEntities = role.permissions().stream()
            .map(p -> PermissionEntity.builder()
                .id(p.id())
                .name(p.name())
                .description(p.description())
                .resource(p.resource())
                .action(p.action())
                .build())
            .toList();

        return RoleEntity.builder()
            .id(role.id())
            .name(role.name())
            .description(role.description())
            .applicationId(role.applicationId())
            .permissions(permissionEntities)
            .build();
    }

    private Role toDomain(RoleEntity entity) {
        List<Permission> permissions = entity.getPermissions().stream()
            .map(p -> new Permission(p.getId(), p.getName(), p.getDescription(), p.getResource(), p.getAction()))
            .toList();

        return new Role(
            entity.getId(), entity.getName(), entity.getDescription(),
            entity.getApplicationId(), permissions, entity.getCreatedAt()
        );
    }
}
