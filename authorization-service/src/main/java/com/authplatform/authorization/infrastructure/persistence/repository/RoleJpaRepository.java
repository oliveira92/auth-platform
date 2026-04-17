package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleEntity, String> {
    List<RoleEntity> findByApplicationId(String applicationId);

    @Query("""
        SELECT r FROM RoleEntity r
        JOIN UserRoleEntity ur ON ur.roleId = r.id
        WHERE ur.username = :username
          AND ur.applicationId = :applicationId
          AND (ur.expiresAt IS NULL OR ur.expiresAt > CURRENT_TIMESTAMP)
        """)
    List<RoleEntity> findByUsernameAndApplicationId(String username, String applicationId);
}
