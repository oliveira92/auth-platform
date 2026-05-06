package com.authplatform.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventJpaRepository
        extends JpaRepository<AuditEventEntity, String>, JpaSpecificationExecutor<AuditEventEntity> {
}
