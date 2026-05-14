package com.authplatform.authorization.infrastructure.persistence.adapter;

import com.authplatform.authorization.domain.model.GroupRoleAssignment;
import com.authplatform.authorization.domain.port.out.GroupRoleRepository;
import com.authplatform.authorization.infrastructure.persistence.entity.GroupRoleEntity;
import com.authplatform.authorization.infrastructure.persistence.repository.GroupRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GroupRoleRepositoryAdapter implements GroupRoleRepository {

    private final GroupRoleJpaRepository jpaRepository;

    @Override
    public GroupRoleAssignment save(GroupRoleAssignment assignment) {
        GroupRoleEntity entity = GroupRoleEntity.builder()
            .id(assignment.id())
            .ldapGroup(assignment.ldapGroup())
            .roleId(assignment.roleId())
            .applicationId(assignment.applicationId())
            .assignedBy(assignment.assignedBy())
            .build();
        GroupRoleEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByLdapGroupAndRoleIdAndApplicationId(
            String ldapGroup, String roleId, String applicationId) {
        return jpaRepository.existsByLdapGroupAndRoleIdAndApplicationId(ldapGroup, roleId, applicationId);
    }

    @Override
    @Transactional
    public void delete(String ldapGroup, String roleId, String applicationId) {
        jpaRepository.deleteByLdapGroupAndRoleIdAndApplicationId(ldapGroup, roleId, applicationId);
    }

    @Override
    public List<GroupRoleAssignment> findByApplicationId(String applicationId) {
        return jpaRepository.findByApplicationId(applicationId).stream()
            .map(this::toDomain)
            .toList();
    }

    private GroupRoleAssignment toDomain(GroupRoleEntity entity) {
        return new GroupRoleAssignment(
            entity.getId(),
            entity.getLdapGroup(),
            entity.getRoleId(),
            entity.getApplicationId(),
            entity.getAssignedBy(),
            entity.getAssignedAt()
        );
    }
}
