package com.delta.dms.ops.dbaccess.repository;

import com.delta.dms.ops.dbaccess.model.Permission;
import com.delta.dms.ops.dbaccess.model.PermissionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for PermissionEvent entity
 */
@Repository
public interface PermissionEventRepository extends JpaRepository<PermissionEvent, Long> {

    List<PermissionEvent> findByPermission(Permission permission);

    List<PermissionEvent> findByPermissionId(Long permissionId);

    List<PermissionEvent> findByEventType(PermissionEvent.EventType eventType);

    @Query("SELECT pe FROM PermissionEvent pe WHERE pe.eventTime BETWEEN :start AND :end ORDER BY pe.eventTime DESC")
    List<PermissionEvent> findEventsBetween(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("SELECT pe FROM PermissionEvent pe WHERE pe.permission.mariadbUsername = :username ORDER BY pe.eventTime DESC")
    List<PermissionEvent> findEventsByMariaDBUsername(@Param("username") String mariadbUsername);

    @Query("SELECT pe FROM PermissionEvent pe WHERE pe.permission.mariadbUsername = :username AND pe.permission.mariadbHost = :host ORDER BY pe.eventTime DESC")
    List<PermissionEvent> findEventsByMariaDBUser(@Param("username") String mariadbUsername, @Param("host") String mariadbHost);
}
