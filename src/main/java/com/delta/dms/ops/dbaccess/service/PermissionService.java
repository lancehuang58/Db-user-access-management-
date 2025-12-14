package com.delta.dms.ops.dbaccess.service;

import com.delta.dms.ops.event.*;
import com.delta.dms.ops.model.Permission;
import com.delta.dms.ops.model.User;
import com.delta.dms.ops.repository.PermissionRepository;
import com.delta.dms.ops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event-driven service for managing permissions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Permission getPermissionById(Long id) {
        return permissionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionsByUserId(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Permission> getActivePermissionsByUserId(Long userId) {
        return permissionRepository.findActivePermissionsByUserId(userId);
    }

    @Transactional
    public Permission createPermission(Permission permission, String createdBy) {
        User user = userRepository.findById(permission.getUser().getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        permission.setUser(user);
        permission.setStatus(Permission.PermissionStatus.PENDING);
        Permission savedPermission = permissionRepository.save(permission);

        // Publish event
        eventPublisher.publishEvent(new PermissionCreatedEvent(this, savedPermission, createdBy));
        log.info("Permission created for user {} by {}", user.getUsername(), createdBy);

        return savedPermission;
    }

    @Transactional
    public Permission approvePermission(Long permissionId, String approvedBy) {
        Permission permission = getPermissionById(permissionId);

        if (permission.getStatus() != Permission.PermissionStatus.PENDING) {
            throw new IllegalStateException("Only pending permissions can be approved");
        }

        permission.setStatus(Permission.PermissionStatus.APPROVED);
        permission.setApprovedBy(approvedBy);
        permission.setApprovedAt(LocalDateTime.now());

        Permission savedPermission = permissionRepository.save(permission);

        // Publish event
        eventPublisher.publishEvent(new PermissionApprovedEvent(this, savedPermission, approvedBy));
        log.info("Permission {} approved by {}", permissionId, approvedBy);

        // Auto-activate if start time has passed
        if (LocalDateTime.now().isAfter(savedPermission.getStartTime())) {
            return activatePermission(permissionId);
        }

        return savedPermission;
    }

    @Transactional
    public Permission activatePermission(Long permissionId) {
        Permission permission = getPermissionById(permissionId);

        if (permission.getStatus() != Permission.PermissionStatus.APPROVED) {
            throw new IllegalStateException("Only approved permissions can be activated");
        }

        permission.setStatus(Permission.PermissionStatus.ACTIVE);
        Permission savedPermission = permissionRepository.save(permission);

        // Publish event
        eventPublisher.publishEvent(new PermissionActivatedEvent(this, savedPermission));
        log.info("Permission {} activated", permissionId);

        return savedPermission;
    }

    @Transactional
    public Permission revokePermission(Long permissionId, String revokedBy) {
        Permission permission = getPermissionById(permissionId);

        permission.setStatus(Permission.PermissionStatus.REVOKED);
        permission.setRevokedBy(revokedBy);
        permission.setRevokedAt(LocalDateTime.now());

        Permission savedPermission = permissionRepository.save(permission);

        // Publish event
        eventPublisher.publishEvent(new PermissionRevokedEvent(this, savedPermission, revokedBy));
        log.info("Permission {} revoked by {}", permissionId, revokedBy);

        return savedPermission;
    }

    @Transactional
    public void processExpiredPermissions() {
        LocalDateTime now = LocalDateTime.now();
        List<Permission> expiredPermissions = permissionRepository
            .findExpiredPermissions(Permission.PermissionStatus.ACTIVE, now);

        for (Permission permission : expiredPermissions) {
            permission.setStatus(Permission.PermissionStatus.EXPIRED);
            permissionRepository.save(permission);

            // Publish event
            eventPublisher.publishEvent(new PermissionExpiredEvent(this, permission));
            log.info("Permission {} expired", permission.getId());
        }
    }

    @Transactional
    public Permission extendPermission(Long permissionId, LocalDateTime newEndTime, String modifiedBy) {
        Permission permission = getPermissionById(permissionId);

        if (newEndTime.isBefore(permission.getEndTime())) {
            throw new IllegalArgumentException("New end time must be after current end time");
        }

        permission.setEndTime(newEndTime);
        return permissionRepository.save(permission);
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionsExpiringBetween(LocalDateTime start, LocalDateTime end) {
        return permissionRepository.findPermissionsExpiringBetween(start, end);
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionsByStatus(Permission.PermissionStatus status) {
        return permissionRepository.findByStatus(status);
    }
}
