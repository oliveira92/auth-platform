package com.authplatform.authorization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_role_assignments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"username", "role_id", "application_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(name = "role_id", nullable = false)
    private String roleId;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "assigned_by")
    private String assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", updatable = false)
    private Instant assignedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
