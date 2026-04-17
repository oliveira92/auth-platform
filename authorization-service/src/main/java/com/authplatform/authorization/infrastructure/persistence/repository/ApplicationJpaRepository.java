package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.ApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationJpaRepository extends JpaRepository<ApplicationEntity, String> {
    Optional<ApplicationEntity> findByClientId(String clientId);
    Optional<ApplicationEntity> findByName(String name);
}
