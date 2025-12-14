package com.delta.dms.ops.dbaccess.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduler for permission-related tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionScheduler {

    private final PermissionService permissionService;
    private final MariaDBEventService mariaDBEventService;

    /**
     * Check and process expired permissions
     * Runs every 5 minutes by default (configurable in application.yml)
     */
    @Scheduled(fixedDelayString = "${app.permission.check-interval:300000}")
    public void processExpiredPermissions() {
        log.debug("Running scheduled permission expiration check");
        try {
            permissionService.processExpiredPermissions();
        } catch (Exception e) {
            log.error("Error processing expired permissions: {}", e.getMessage(), e);
        }
    }

    /**
     * Check MariaDB Event Scheduler status on startup
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void checkEventScheduler() {
        try {
            if (!mariaDBEventService.isEventSchedulerEnabled()) {
                log.warn("MariaDB Event Scheduler is NOT enabled!");
                log.warn("Attempting to enable Event Scheduler...");
                mariaDBEventService.enableEventScheduler();
                log.info("MariaDB Event Scheduler has been enabled");
            } else {
                log.info("MariaDB Event Scheduler is enabled and running");
            }
        } catch (Exception e) {
            log.error("Error checking/enabling MariaDB Event Scheduler: {}", e.getMessage(), e);
            log.error("Please ensure your MariaDB user has SUPER or SYSTEM_VARIABLES_ADMIN privilege");
            log.error("Or manually enable event scheduler with: SET GLOBAL event_scheduler = ON;");
        }
    }
}
