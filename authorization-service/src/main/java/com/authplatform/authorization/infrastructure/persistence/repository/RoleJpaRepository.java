package com.authplatform.authorization.infrastructure.persistence.repository;

import com.authplatform.authorization.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Model 1 — LDAP as source of truth.
     * Finds all Roles assigned to any of the provided LDAP groups within the given application.
     * The ldapGroups list must match the raw group names present in the JWT "groups" claim.
     */
    @Query("""
        SELECT DISTINCT r FROM RoleEntity r
        JOIN GroupRoleEntity gr ON gr.roleId = r.id
        WHERE gr.ldapGroup IN :ldapGroups
          AND gr.applicationId = :applicationId
        """)
    List<RoleEntity> findByLdapGroupsAndApplicationId(
        @Param("ldapGroups") List<String> ldapGroups,
        @Param("applicationId") String applicationId);
}
