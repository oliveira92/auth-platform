package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, String> {
    Optional<PermissionEntity> findByResourceAndAction(String resource, String action);
}
