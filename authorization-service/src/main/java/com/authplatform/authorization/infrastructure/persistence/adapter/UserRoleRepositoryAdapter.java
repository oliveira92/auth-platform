package com.authplatform.authorization.infrastructure.persistence.adapter;

import com.authplatform.authorization.domain.model.UserRoleAssignment;
import com.authplatform.authorization.domain.port.out.UserRoleRepository;
import com.authplatform.authorization.infrastructure.persistence.entity.UserRoleEntity;
import com.authplatform.authorization.infrastructure.persistence.repository.UserRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserRoleRepositoryAdapter implements UserRoleRepository {

    private final UserRoleJpaRepository jpaRepository;

    @Override
    public UserRoleAssignment save(UserRoleAssignment assignment) {
        UserRoleEntity entity = toEntity(assignment);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<UserRoleAssignment> findByUsernameAndApplicationId(String username, String applicationId) {
        return jpaRepository.findByUsernameAndApplicationId(username, applicationId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void delete(String username, String roleId, String applicationId) {
        jpaRepository.findByUsernameAndRoleIdAndApplicationId(username, roleId, applicationId)
            .ifPresent(entity -> jpaRepository.deleteById(entity.getId()));
    }

    @Override
    public void deleteAllForUser(String username) {
        jpaRepository.deleteByUsername(username);
    }

    private UserRoleEntity toEntity(UserRoleAssignment assignment) {
        return UserRoleEntity.builder()
            .id(assignment.id())
            .username(assignment.username())
            .roleId(assignment.roleId())
            .applicationId(assignment.applicationId())
            .assignedBy(assignment.assignedBy())
            .expiresAt(assignment.expiresAt())
            .build();
    }

    private UserRoleAssignment toDomain(UserRoleEntity entity) {
        return new UserRoleAssignment(
            entity.getId(), entity.getUsername(), entity.getRoleId(),
            entity.getApplicationId(), entity.getAssignedBy(),
            entity.getAssignedAt(), entity.getExpiresAt()
        );
    }
}
