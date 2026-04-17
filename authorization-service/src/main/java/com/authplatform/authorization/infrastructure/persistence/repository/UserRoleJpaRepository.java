package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, String> {
    List<UserRoleEntity> findByUsernameAndApplicationId(String username, String applicationId);
    Optional<UserRoleEntity> findByUsernameAndRoleIdAndApplicationId(String username, String roleId, String applicationId);
    void deleteByUsername(String username);
}
