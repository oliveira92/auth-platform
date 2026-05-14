package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.GroupRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRoleJpaRepository extends JpaRepository<GroupRoleEntity, String> {

    List<GroupRoleEntity> findByApplicationId(String applicationId);

    Optional<GroupRoleEntity> findByLdapGroupAndRoleIdAndApplicationId(
        String ldapGroup, String roleId, String applicationId);

    boolean existsByLdapGroupAndRoleIdAndApplicationId(
        String ldapGroup, String roleId, String applicationId);

    void deleteByLdapGroupAndRoleIdAndApplicationId(
        String ldapGroup, String roleId, String applicationId);
}
