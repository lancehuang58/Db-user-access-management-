package com.delta.dms.ops.dbaccess.repository;

import com.delta.dms.ops.model.Permission;
import com.delta.dms.ops.model.User;
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

    List<Permission> findByUser(User user);

    List<Permission> findByUserId(Long userId);

    List<Permission> findByStatus(Permission.PermissionStatus status);

    @Query("SELECT p FROM Permission p WHERE p.status = :status AND p.endTime < :now")
    List<Permission> findExpiredPermissions(
        @Param("status") Permission.PermissionStatus status,
        @Param("now") LocalDateTime now
    );

    @Query("SELECT p FROM Permission p WHERE p.user.id = :userId AND p.status = 'ACTIVE'")
    List<Permission> findActivePermissionsByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Permission p WHERE p.status = 'ACTIVE' AND p.endTime BETWEEN :start AND :end")
    List<Permission> findPermissionsExpiringBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
