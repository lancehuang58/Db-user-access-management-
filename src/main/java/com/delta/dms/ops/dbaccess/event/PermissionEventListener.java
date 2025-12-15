package com.delta.dms.ops.dbaccess.event;

import com.delta.dms.ops.dbaccess.exception.MariaDBOperationException;
import com.delta.dms.ops.dbaccess.exception.MariaDBValidationException;
import com.delta.dms.ops.dbaccess.model.Permission;
import com.delta.dms.ops.dbaccess.model.PermissionEvent;
import com.delta.dms.ops.dbaccess.repository.PermissionEventRepository;
import com.delta.dms.ops.dbaccess.service.MariaDBEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Event listener for permission-related events
 * Handles MariaDB event creation and permission event logging
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionEventListener {

    private final PermissionEventRepository eventRepository;
    private final MariaDBEventService mariaDBEventService;

    @EventListener
    @Async
    @Transactional
    public void handlePermissionCreated(PermissionCreatedEvent event) {
        log.info("Handling PermissionCreatedEvent for permission {}", event.getPermission().getId());

        // Log the event
        PermissionEvent permissionEvent = new PermissionEvent();
        permissionEvent.setPermission(event.getPermission());
        permissionEvent.setEventType(PermissionEvent.EventType.CREATED);
        permissionEvent.setTriggeredBy(event.getCreatedBy());
        permissionEvent.setEventDetails(String.format(
            "Permission created for MariaDB user '%s'@'%s' on resource %s",
            event.getPermission().getMariadbUsername(),
            event.getPermission().getMariadbHost(),
            event.getPermission().getResourceName()
        ));
        permissionEvent.setEventTime(LocalDateTime.now());
        eventRepository.save(permissionEvent);
    }

    @EventListener
    @Async
    @Transactional
    public void handlePermissionApproved(PermissionApprovedEvent event) {
        log.info("Handling PermissionApprovedEvent for permission {}", event.getPermission().getId());

        // Log the event
        PermissionEvent permissionEvent = new PermissionEvent();
        permissionEvent.setPermission(event.getPermission());
        permissionEvent.setEventType(PermissionEvent.EventType.APPROVED);
        permissionEvent.setTriggeredBy(event.getApprovedBy());
        permissionEvent.setEventDetails(String.format(
            "Permission approved by %s", event.getApprovedBy()
        ));
        permissionEvent.setEventTime(LocalDateTime.now());
        eventRepository.save(permissionEvent);
    }

    @EventListener
    @Async
    @Transactional
    @Retryable(
        retryFor = {DataAccessException.class, MariaDBOperationException.class},
        noRetryFor = {MariaDBValidationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
    )
    public void handlePermissionActivated(PermissionActivatedEvent event) {
        log.info("Handling PermissionActivatedEvent for permission {}", event.getPermission().getId());

        Permission permission = event.getPermission();

        // Grant database permission and create MariaDB event for auto-revoke
        try {
            mariaDBEventService.grantPermissionWithAutoRevoke(permission);

            // Log the event
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.ACTIVATED);
            permissionEvent.setTriggeredBy("SYSTEM");
            permissionEvent.setEventDetails(String.format(
                "Permission activated. MariaDB access granted to user '%s'@'%s' on %s with auto-revoke scheduled at %s",
                permission.getMariadbUsername(),
                permission.getMariadbHost(),
                permission.getResourceName(),
                permission.getEndTime()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);

        } catch (MariaDBValidationException e) {
            // Validation errors should not be retried
            log.error("Validation error activating permission: {}", e.getMessage(), e);

            // Log the failure
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.ACTIVATED);
            permissionEvent.setTriggeredBy("SYSTEM");
            permissionEvent.setEventDetails(String.format(
                "Failed to activate permission (validation error): %s", e.getMessage()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);

        } catch (MariaDBOperationException e) {
            // Check if retryable
            if (e.isRetryable()) {
                log.warn("Retryable error activating permission (will retry): {}", e.getMessage());
                throw e;  // Re-throw to trigger retry
            } else {
                log.error("Non-retryable error activating permission: {}", e.getMessage(), e);

                // Log the failure
                PermissionEvent permissionEvent = new PermissionEvent();
                permissionEvent.setPermission(permission);
                permissionEvent.setEventType(PermissionEvent.EventType.ACTIVATED);
                permissionEvent.setTriggeredBy("SYSTEM");
                permissionEvent.setEventDetails(String.format(
                    "Failed to activate permission: %s", e.getMessage()
                ));
                permissionEvent.setEventTime(LocalDateTime.now());
                eventRepository.save(permissionEvent);
            }

        } catch (Exception e) {
            log.error("Unexpected error activating permission: {}", e.getMessage(), e);

            // Log the failure
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.ACTIVATED);
            permissionEvent.setTriggeredBy("SYSTEM");
            permissionEvent.setEventDetails(String.format(
                "Failed to activate permission: %s", e.getMessage()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);
        }
    }

    @EventListener
    @Async
    @Transactional
    @Retryable(
        retryFor = {DataAccessException.class, MariaDBOperationException.class},
        noRetryFor = {MariaDBValidationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
    )
    public void handlePermissionRevoked(PermissionRevokedEvent event) {
        log.info("Handling PermissionRevokedEvent for permission {}", event.getPermission().getId());

        Permission permission = event.getPermission();

        // Revoke MariaDB permission immediately
        try {
            mariaDBEventService.revokePermissionNow(permission);

            // Log the event
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.REVOKED);
            permissionEvent.setTriggeredBy(event.getRevokedBy());
            permissionEvent.setEventDetails(String.format(
                "Permission revoked by %s. MariaDB access removed from user '%s'@'%s'",
                event.getRevokedBy(),
                permission.getMariadbUsername(),
                permission.getMariadbHost()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);

        } catch (MariaDBValidationException e) {
            // Validation errors should not be retried
            log.error("Validation error revoking permission: {}", e.getMessage(), e);

            // Log the failure
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.REVOKED);
            permissionEvent.setTriggeredBy(event.getRevokedBy());
            permissionEvent.setEventDetails(String.format(
                "Failed to revoke permission (validation error): %s", e.getMessage()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);

        } catch (MariaDBOperationException e) {
            // Check if retryable
            if (e.isRetryable()) {
                log.warn("Retryable error revoking permission (will retry): {}", e.getMessage());
                throw e;  // Re-throw to trigger retry
            } else {
                log.error("Non-retryable error revoking permission: {}", e.getMessage(), e);

                // Log the failure
                PermissionEvent permissionEvent = new PermissionEvent();
                permissionEvent.setPermission(permission);
                permissionEvent.setEventType(PermissionEvent.EventType.REVOKED);
                permissionEvent.setTriggeredBy(event.getRevokedBy());
                permissionEvent.setEventDetails(String.format(
                    "Failed to revoke permission: %s", e.getMessage()
                ));
                permissionEvent.setEventTime(LocalDateTime.now());
                eventRepository.save(permissionEvent);
            }

        } catch (Exception e) {
            log.error("Unexpected error revoking permission: {}", e.getMessage(), e);

            // Log the failure
            PermissionEvent permissionEvent = new PermissionEvent();
            permissionEvent.setPermission(permission);
            permissionEvent.setEventType(PermissionEvent.EventType.REVOKED);
            permissionEvent.setTriggeredBy(event.getRevokedBy());
            permissionEvent.setEventDetails(String.format(
                "Failed to revoke permission: %s", e.getMessage()
            ));
            permissionEvent.setEventTime(LocalDateTime.now());
            eventRepository.save(permissionEvent);
        }
    }

    @EventListener
    @Async
    @Transactional
    public void handlePermissionExpired(PermissionExpiredEvent event) {
        log.info("Handling PermissionExpiredEvent for permission {}", event.getPermission().getId());

        Permission permission = event.getPermission();

        // Log the event (MariaDB event should have already revoked the permission)
        PermissionEvent permissionEvent = new PermissionEvent();
        permissionEvent.setPermission(permission);
        permissionEvent.setEventType(PermissionEvent.EventType.EXPIRED);
        permissionEvent.setTriggeredBy("SYSTEM");
        permissionEvent.setEventDetails(String.format(
            "Permission expired. MariaDB access should be automatically revoked by scheduled event for user '%s'@'%s'",
            permission.getMariadbUsername(),
            permission.getMariadbHost()
        ));
        permissionEvent.setEventTime(LocalDateTime.now());
        eventRepository.save(permissionEvent);
    }
}
