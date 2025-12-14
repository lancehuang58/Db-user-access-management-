package com.delta.dms.ops.dbaccess.service;

import com.delta.dms.ops.dbaccess.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for managing MariaDB users and database events for automatic permission revocation
 */
@Service
@Slf4j
public class MariaDBEventService {

    private final JdbcTemplate jdbcTemplate;

    public MariaDBEventService(@Qualifier("mariadbJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Grant database permission to a user and create a MariaDB event to auto-revoke
     */
    @Transactional
    public void grantPermissionWithAutoRevoke(Permission permission) {
        String dbUsername = permission.getUser().getUsername();
        String resourceName = permission.getResourceName();
        Permission.PermissionType type = permission.getType();
        LocalDateTime endTime = permission.getEndTime();

        // Create or update MariaDB user if needed
        ensureMariaDBUserExists(dbUsername);

        // Grant the appropriate permissions
        grantDatabasePermission(dbUsername, resourceName, type);

        // Create MariaDB event for auto-revoke
        createRevokeEvent(permission.getId(), dbUsername, resourceName, type, endTime);

        log.info("Granted {} permission on {} to user {} with auto-revoke at {}",
                 type, resourceName, dbUsername, endTime);
    }

    /**
     * Ensure MariaDB user exists, create if not
     */
    private void ensureMariaDBUserExists(String username) {
        try {
            // Check if user exists
            String checkUserSql = "SELECT COUNT(*) FROM mysql.user WHERE user = ?";
            Integer count = jdbcTemplate.queryForObject(checkUserSql, Integer.class, username);

            if (count == null || count == 0) {
                // Create user with random password (user should change it)
                String createUserSql = String.format(
                    "CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s'",
                    escapeSql(username),
                    generateRandomPassword()
                );
                jdbcTemplate.execute(createUserSql);
                log.info("Created MariaDB user: {}", username);
            }
        } catch (Exception e) {
            log.error("Error ensuring MariaDB user exists: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create/verify MariaDB user", e);
        }
    }

    /**
     * Grant database permissions based on permission type
     */
    private void grantDatabasePermission(String username, String resourceName, Permission.PermissionType type) {
        try {
            String privileges = getPrivilegesForType(type);
            String grantSql;

            // Resource name can be database name, table name (db.table), or * for all
            if (resourceName.contains(".")) {
                // Grant on specific table
                grantSql = String.format(
                    "GRANT %s ON %s TO '%s'@'%%'",
                    privileges,
                    resourceName,
                    escapeSql(username)
                );
            } else if ("*".equals(resourceName)) {
                // Grant on all databases
                grantSql = String.format(
                    "GRANT %s ON *.* TO '%s'@'%%'",
                    privileges,
                    escapeSql(username)
                );
            } else {
                // Grant on specific database
                grantSql = String.format(
                    "GRANT %s ON %s.* TO '%s'@'%%'",
                    privileges,
                    escapeSql(resourceName),
                    escapeSql(username)
                );
            }

            jdbcTemplate.execute(grantSql);
            jdbcTemplate.execute("FLUSH PRIVILEGES");
            log.info("Granted {} privileges on {} to {}", privileges, resourceName, username);

        } catch (Exception e) {
            log.error("Error granting database permission: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to grant database permission", e);
        }
    }

    /**
     * Create a MariaDB event to automatically revoke permissions at end time
     */
    private void createRevokeEvent(Long permissionId, String username, String resourceName,
                                   Permission.PermissionType type, LocalDateTime endTime) {
        try {
            String eventName = String.format("revoke_perm_%d", permissionId);
            String privileges = getPrivilegesForType(type);
            String revokeStatement;

            // Build revoke statement based on resource type
            if (resourceName.contains(".")) {
                revokeStatement = String.format(
                    "REVOKE %s ON %s FROM '%s'@'%%'",
                    privileges,
                    resourceName,
                    escapeSql(username)
                );
            } else if ("*".equals(resourceName)) {
                revokeStatement = String.format(
                    "REVOKE %s ON *.* FROM '%s'@'%%'",
                    privileges,
                    escapeSql(username)
                );
            } else {
                revokeStatement = String.format(
                    "REVOKE %s ON %s.* FROM '%s'@'%%'",
                    privileges,
                    escapeSql(resourceName),
                    escapeSql(username)
                );
            }

            // Drop existing event if it exists
            String dropEventSql = String.format("DROP EVENT IF EXISTS %s", eventName);
            jdbcTemplate.execute(dropEventSql);

            // Create new event
            String createEventSql = String.format(
                "CREATE EVENT %s " +
                "ON SCHEDULE AT '%s' " +
                "DO BEGIN " +
                "  %s; " +
                "  FLUSH PRIVILEGES; " +
                "END",
                eventName,
                endTime.format(DATETIME_FORMATTER),
                revokeStatement
            );

            jdbcTemplate.execute(createEventSql);
            log.info("Created MariaDB event {} to revoke permissions at {}", eventName, endTime);

        } catch (Exception e) {
            log.error("Error creating revoke event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create MariaDB revoke event", e);
        }
    }

    /**
     * Manually revoke permission immediately
     */
    @Transactional
    public void revokePermissionNow(Permission permission) {
        try {
            String username = permission.getUser().getUsername();
            String resourceName = permission.getResourceName();
            Permission.PermissionType type = permission.getType();
            String privileges = getPrivilegesForType(type);

            String revokeSql;
            if (resourceName.contains(".")) {
                revokeSql = String.format(
                    "REVOKE %s ON %s FROM '%s'@'%%'",
                    privileges,
                    resourceName,
                    escapeSql(username)
                );
            } else if ("*".equals(resourceName)) {
                revokeSql = String.format(
                    "REVOKE %s ON *.* FROM '%s'@'%%'",
                    privileges,
                    escapeSql(username)
                );
            } else {
                revokeSql = String.format(
                    "REVOKE %s ON %s.* FROM '%s'@'%%'",
                    privileges,
                    escapeSql(resourceName),
                    escapeSql(username)
                );
            }

            jdbcTemplate.execute(revokeSql);
            jdbcTemplate.execute("FLUSH PRIVILEGES");

            // Cancel the scheduled event
            String eventName = String.format("revoke_perm_%d", permission.getId());
            String dropEventSql = String.format("DROP EVENT IF EXISTS %s", eventName);
            jdbcTemplate.execute(dropEventSql);

            log.info("Revoked {} permissions on {} from {}", privileges, resourceName, username);

        } catch (Exception e) {
            log.error("Error revoking permission: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to revoke database permission", e);
        }
    }

    /**
     * Get MariaDB privileges string based on permission type
     */
    private String getPrivilegesForType(Permission.PermissionType type) {
        return switch (type) {
            case READ -> "SELECT";
            case WRITE -> "SELECT, INSERT, UPDATE";
            case DELETE -> "SELECT, DELETE";
            case EXECUTE -> "EXECUTE";
            case ADMIN -> "ALL PRIVILEGES";
        };
    }

    /**
     * Simple SQL escaping for usernames and identifiers
     */
    private String escapeSql(String input) {
        if (input == null) {
            return null;
        }
        // Remove potentially dangerous characters
        return input.replaceAll("[^a-zA-Z0-9_.]", "");
    }

    /**
     * Generate a random password for new users
     */
    private String generateRandomPassword() {
        // Generate a random 16-character password
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 16; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    /**
     * Check if MariaDB Event Scheduler is enabled
     */
    public boolean isEventSchedulerEnabled() {
        try {
            String sql = "SHOW VARIABLES LIKE 'event_scheduler'";
            String status = jdbcTemplate.queryForObject(sql,
                (rs, rowNum) -> rs.getString("Value"));
            return "ON".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.error("Error checking event scheduler status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Enable MariaDB Event Scheduler
     */
    public void enableEventScheduler() {
        try {
            jdbcTemplate.execute("SET GLOBAL event_scheduler = ON");
            log.info("Enabled MariaDB event scheduler");
        } catch (Exception e) {
            log.error("Error enabling event scheduler: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to enable event scheduler", e);
        }
    }
}
