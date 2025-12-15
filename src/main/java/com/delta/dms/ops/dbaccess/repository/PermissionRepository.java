package com.delta.dms.ops.dbaccess.repository;

import com.delta.dms.ops.dbaccess.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Permission entity
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    // Find by MariaDB username
    List<Permission> findByMariadbUsername(String mariadbUsername);

    // Find by MariaDB username and host
    List<Permission> findByMariadbUsernameAndMariadbHost(String mariadbUsername, String mariadbHost);

    // Find by status
    List<Permission> findByStatus(Permission.PermissionStatus status);

    // Find expired permissions
    @Query("SELECT p FROM Permission p WHERE p.status = :status AND p.endTime < :now")
    List<Permission> findExpiredPermissions(
        @Param("status") Permission.PermissionStatus status,
        @Param("now") LocalDateTime now
    );

    // Find active permissions by MariaDB username
    @Query("SELECT p FROM Permission p WHERE p.mariadbUsername = :username AND p.status = 'ACTIVE'")
    List<Permission> findActivePermissionsByMariaDBUsername(@Param("username") String username);

    // Find permissions expiring soon
    @Query("SELECT p FROM Permission p WHERE p.status = 'ACTIVE' AND p.endTime BETWEEN :start AND :end")
    List<Permission> findPermissionsExpiringBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
