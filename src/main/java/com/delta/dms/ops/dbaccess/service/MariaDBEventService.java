package com.delta.dms.ops.dbaccess.service;

import com.delta.dms.ops.dbaccess.exception.MariaDBOperationException;
import com.delta.dms.ops.dbaccess.exception.MariaDBPermissionException;
import com.delta.dms.ops.dbaccess.exception.MariaDBUserNotFoundException;
import com.delta.dms.ops.dbaccess.exception.MariaDBValidationException;
import com.delta.dms.ops.dbaccess.model.Permission;
import com.delta.dms.ops.dbaccess.util.MariaDBQueryBuilder;
import com.delta.dms.ops.dbaccess.util.MariaDBValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing MariaDB users and database events for automatic permission revocation.
 * Uses secure SQL query construction to prevent SQL injection.
 */
@Service
@Slf4j
public class MariaDBEventService {

    private final JdbcTemplate jdbcTemplate;
    private final SecureRandom secureRandom;

    @Value("${mariadb.user-management.default-host:%}")
    private String defaultHost;

    @Value("${mariadb.user-management.password-length:20}")
    private int passwordLength;

    public MariaDBEventService(@Qualifier("mariadbJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Grant database permission to a user and create a MariaDB event to auto-revoke.
     * @param permission The permission object containing user, resource, type, and time range
     * @throws MariaDBValidationException if permission validation fails
     * @throws MariaDBPermissionException if grant operation fails
     */
    @Transactional
    public void grantPermissionWithAutoRevoke(Permission permission) {
        // Validate permission
        MariaDBValidator.validatePermission(permission);

        String dbUsername = permission.getMariadbUsername();
        String dbHost = permission.getMariadbHost();
        String resourceName = permission.getResourceName();
        Permission.PermissionType type = permission.getType();
        LocalDateTime endTime = permission.getEndTime();

        log.info("Granting {} permission on {} to user '{}@{}' with auto-revoke at {}",
                 type, resourceName, dbUsername, dbHost, endTime);

        try {
            // Create or update MariaDB user if needed
            ensureMariaDBUserExists(dbUsername, dbHost);

            // Grant the appropriate permissions
            grantDatabasePermission(dbUsername, dbHost, resourceName, type);

            // Create MariaDB event for auto-revoke
            createRevokeEvent(permission.getId(), dbUsername, dbHost, resourceName, type, endTime);

            log.info("Successfully granted {} permission on {} to user '{}@{}'", type, resourceName, dbUsername, dbHost);

        } catch (MariaDBValidationException | MariaDBPermissionException e) {
            // Re-throw validation and permission exceptions
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error granting permission: {}", e.getMessage(), e);
            throw MariaDBPermissionException.grantFailed(dbUsername, resourceName, e);
        } catch (Exception e) {
            log.error("Unexpected error granting permission: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to grant permission with auto-revoke", e);
        }
    }

    /**
     * Ensure MariaDB user exists, create if not.
     * Uses cryptographically secure password generation.
     * @param username The username to check/create
     * @param host The host pattern for the user
     * @throws MariaDBValidationException if username or host validation fails
     * @throws MariaDBOperationException if user creation fails
     */
    private void ensureMariaDBUserExists(String username, String host) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);

        try {
            // Check if user exists
            String checkUserSql = MariaDBQueryBuilder.buildCheckUserExistsQuery();
            Integer count = jdbcTemplate.queryForObject(checkUserSql, Integer.class, username, host);

            if (count == null || count == 0) {
                // Generate secure random password
                String password = generateSecurePassword();

                // Create user with parameterized query
                String createUserSql = MariaDBQueryBuilder.buildCreateUser(username, host);
                jdbcTemplate.update(createUserSql, password);

                log.info("Created MariaDB user '{}' @'{}'", username, host);
                log.warn("Generated password for user '{}' - Password should be securely communicated to user", username);
                // TODO: Store encrypted password for one-time retrieval or send securely to user
            } else {
                log.debug("MariaDB user '{}' @'{}' already exists", username, host);
            }
        } catch (MariaDBValidationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error ensuring user exists: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to create/verify MariaDB user '" + username + "'", e);
        } catch (Exception e) {
            log.error("Unexpected error ensuring user exists: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to ensure MariaDB user exists", e);
        }
    }

    /**
     * Grant database permissions based on permission type.
     * @param username The username to grant permissions to
     * @param host The host pattern for the user
     * @param resourceName The resource to grant permissions on (database or database.table)
     * @param type The permission type (READ, WRITE, DELETE, EXECUTE, ADMIN)
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBPermissionException if grant operation fails
     */
    private void grantDatabasePermission(String username, String host, String resourceName, Permission.PermissionType type) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);
        MariaDBValidator.validateResourceName(resourceName);

        try {
            // Get privileges list for this permission type
            List<String> privileges = MariaDBQueryBuilder.getPrivilegesForType(type);

            // Determine resource type (GLOBAL, DATABASE, or TABLE)
            MariaDBQueryBuilder.ResourceType resourceType = MariaDBQueryBuilder.determineResourceType(resourceName);

            // Build and execute GRANT statement
            String grantSql = MariaDBQueryBuilder.buildGrantStatement(
                privileges, resourceType, resourceName, username, host
            );

            jdbcTemplate.execute(grantSql);
            // Note: FLUSH PRIVILEGES removed - not needed in modern MariaDB

            log.info("Granted {} privileges on {} to '{}' @'{}'",
                     String.join(", ", privileges), resourceName, username, host);

        } catch (MariaDBValidationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error granting permission: {}", e.getMessage(), e);
            throw MariaDBPermissionException.grantFailed(username, resourceName, e);
        } catch (Exception e) {
            log.error("Unexpected error granting permission: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to grant database permission", e);
        }
    }

    /**
     * Create a MariaDB event to automatically revoke permissions at end time.
     * @param permissionId The permission ID (used for event naming)
     * @param username The username to revoke permissions from
     * @param host The host pattern for the user
     * @param resourceName The resource to revoke permissions on
     * @param type The permission type to revoke
     * @param endTime The time when permissions should be revoked
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBOperationException if event creation fails
     */
    private void createRevokeEvent(Long permissionId, String username, String host,
                                   String resourceName, Permission.PermissionType type,
                                   LocalDateTime endTime) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);
        MariaDBValidator.validateResourceName(resourceName);

        if (permissionId == null) {
            throw MariaDBValidationException.missingParameter("permissionId");
        }

        if (endTime == null) {
            throw MariaDBValidationException.missingParameter("endTime");
        }

        try {
            String eventName = String.format("revoke_perm_%d", permissionId);
            MariaDBValidator.validateEventName(eventName);

            // Get privileges list
            List<String> privileges = MariaDBQueryBuilder.getPrivilegesForType(type);

            // Determine resource type
            MariaDBQueryBuilder.ResourceType resourceType = MariaDBQueryBuilder.determineResourceType(resourceName);

            // Drop existing event if it exists
            String dropEventSql = MariaDBQueryBuilder.buildDropEvent(eventName);
            jdbcTemplate.execute(dropEventSql);

            // Create new event with safe SQL construction
            String createEventSql = MariaDBQueryBuilder.buildCreateRevokeEvent(
                eventName, endTime, privileges, resourceType, resourceName, username, host
            );

            jdbcTemplate.execute(createEventSql);
            log.info("Created MariaDB event '{}' to revoke permissions at {}", eventName, endTime);

        } catch (MariaDBValidationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error creating revoke event: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to create MariaDB revoke event", e);
        } catch (Exception e) {
            log.error("Unexpected error creating revoke event: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to create MariaDB revoke event", e);
        }
    }

    /**
     * Manually revoke permission immediately.
     * Also cancels the scheduled auto-revoke event.
     * @param permission The permission to revoke
     * @throws MariaDBValidationException if permission validation fails
     * @throws MariaDBPermissionException if revoke operation fails
     */
    @Transactional
    public void revokePermissionNow(Permission permission) {
        if (permission == null) {
            throw MariaDBValidationException.missingParameter("permission");
        }

        String username = permission.getMariadbUsername();
        String host = permission.getMariadbHost();
        String resourceName = permission.getResourceName();
        Permission.PermissionType type = permission.getType();

        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);
        MariaDBValidator.validateResourceName(resourceName);

        log.info("Revoking {} permission on {} from user '{}@{}'", type, resourceName, username, host);

        try {
            // Get privileges list
            List<String> privileges = MariaDBQueryBuilder.getPrivilegesForType(type);

            // Determine resource type
            MariaDBQueryBuilder.ResourceType resourceType = MariaDBQueryBuilder.determineResourceType(resourceName);

            // Build and execute REVOKE statement
            String revokeSql = MariaDBQueryBuilder.buildRevokeStatement(
                privileges, resourceType, resourceName, username, host
            );

            jdbcTemplate.execute(revokeSql);
            // Note: FLUSH PRIVILEGES removed - not needed in modern MariaDB

            // Cancel the scheduled event
            String eventName = String.format("revoke_perm_%d", permission.getId());
            String dropEventSql = MariaDBQueryBuilder.buildDropEvent(eventName);
            jdbcTemplate.execute(dropEventSql);

            log.info("Revoked {} permissions on {} from '{}@{}'", String.join(", ", privileges), resourceName, username, host);

        } catch (MariaDBValidationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error revoking permission: {}", e.getMessage(), e);
            throw MariaDBPermissionException.revokeFailed(username, resourceName, e);
        } catch (Exception e) {
            log.error("Unexpected error revoking permission: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to revoke database permission", e);
        }
    }

    /**
     * Generate a cryptographically secure random password.
     * Uses SecureRandom and only alphanumeric characters to avoid escaping issues.
     * @return A secure random password
     */
    private String generateSecurePassword() {
        // Use only alphanumeric characters to avoid SQL escaping issues
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < passwordLength; i++) {
            int randomIndex = secureRandom.nextInt(chars.length());
            password.append(chars.charAt(randomIndex));
        }

        return password.toString();
    }

    // ==================== User Management CRUD Operations ====================

    /**
     * Create a MariaDB user with explicit password.
     * @param username The username to create
     * @param host The host pattern for the user
     * @param password The password for the user
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBOperationException if user creation fails
     */
    @Transactional
    public void createMariaDBUser(String username, String host, String password) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);
        MariaDBValidator.validatePassword(password);

        log.info("Creating MariaDB user '{}' @'{}'", username, host);

        try {
            // Check if user already exists
            if (userExists(username, host)) {
                throw new MariaDBOperationException(
                    String.format("User '%s'@'%s' already exists", username, host),
                    "USER_EXISTS",
                    false
                );
            }

            // Create user with parameterized query
            String createUserSql = MariaDBQueryBuilder.buildCreateUser(username, host);
            jdbcTemplate.update(createUserSql, password);

            log.info("Successfully created MariaDB user '{}' @'{}'", username, host);

        } catch (MariaDBOperationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error creating user: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to create MariaDB user", e);
        } catch (Exception e) {
            log.error("Unexpected error creating user: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to create MariaDB user", e);
        }
    }

    /**
     * Drop a MariaDB user.
     * @param username The username to drop
     * @param host The host pattern for the user
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBUserNotFoundException if user doesn't exist
     * @throws MariaDBOperationException if drop operation fails
     */
    @Transactional
    public void dropMariaDBUser(String username, String host) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);

        log.info("Dropping MariaDB user '{}' @'{}'", username, host);

        try {
            // Check if user exists
            if (!userExists(username, host)) {
                throw new MariaDBUserNotFoundException(username, host);
            }

            // Drop user
            String dropUserSql = MariaDBQueryBuilder.buildDropUser(username, host);
            jdbcTemplate.execute(dropUserSql);

            log.info("Successfully dropped MariaDB user '{}' @'{}'", username, host);

        } catch (MariaDBValidationException | MariaDBUserNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error dropping user: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to drop MariaDB user", e);
        } catch (Exception e) {
            log.error("Unexpected error dropping user: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to drop MariaDB user", e);
        }
    }

    /**
     * Change a MariaDB user's password.
     * @param username The username
     * @param host The host pattern for the user
     * @param newPassword The new password
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBUserNotFoundException if user doesn't exist
     * @throws MariaDBOperationException if password change fails
     */
    @Transactional
    public void alterUserPassword(String username, String host, String newPassword) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);
        MariaDBValidator.validatePassword(newPassword);

        log.info("Changing password for MariaDB user '{}' @'{}'", username, host);

        try {
            // Check if user exists
            if (!userExists(username, host)) {
                throw new MariaDBUserNotFoundException(username, host);
            }

            // Alter user password
            String alterUserSql = MariaDBQueryBuilder.buildAlterUserPassword(username, host);
            jdbcTemplate.update(alterUserSql, newPassword);

            log.info("Successfully changed password for MariaDB user '{}' @'{}'", username, host);

        } catch (MariaDBValidationException | MariaDBUserNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error changing password: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to change user password", e);
        } catch (Exception e) {
            log.error("Unexpected error changing password: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to change user password", e);
        }
    }

    /**
     * List all MariaDB users (excluding system users).
     * @return List of MariaDB user information
     * @throws MariaDBOperationException if query fails
     */
    public List<com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo> listMariaDBUsers() {
        log.debug("Listing all MariaDB users");

        try {
            String sql = MariaDBQueryBuilder.buildListUsersQuery();
            return jdbcTemplate.query(sql, (rs, rowNum) ->
                new com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo(
                    rs.getString("user"),
                    rs.getString("host"),
                    "N",  // account_locked - default to unlocked for compatibility
                    "N"   // password_expired - default to not expired for compatibility
                )
            );
        } catch (DataAccessException e) {
            log.error("Database error listing users: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to list MariaDB users", e);
        } catch (Exception e) {
            log.error("Unexpected error listing users: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to list MariaDB users", e);
        }
    }

    /**
     * Get information about a specific MariaDB user.
     * @param username The username
     * @param host The host pattern
     * @return User information
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBUserNotFoundException if user doesn't exist
     * @throws MariaDBOperationException if query fails
     */
    public com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo getUserInfo(String username, String host) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);

        log.debug("Getting info for MariaDB user '{}' @'{}'", username, host);

        try {
            String sql = MariaDBQueryBuilder.buildGetUserInfoQuery();
            List<com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new com.delta.dms.ops.dbaccess.dto.MariaDBUserInfo(
                    rs.getString("user"),
                    rs.getString("host"),
                    "N",  // account_locked - default to unlocked for compatibility
                    "N"   // password_expired - default to not expired for compatibility
                ),
                username,
                host
            );

            if (users.isEmpty()) {
                throw new MariaDBUserNotFoundException(username, host);
            }

            return users.get(0);

        } catch (MariaDBValidationException | MariaDBUserNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error getting user info: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to get user information", e);
        } catch (Exception e) {
            log.error("Unexpected error getting user info: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to get user information", e);
        }
    }

    /**
     * Check if a MariaDB user exists.
     * @param username The username to check
     * @param host The host pattern
     * @return true if user exists, false otherwise
     * @throws MariaDBValidationException if validation fails
     */
    public boolean userExists(String username, String host) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);

        try {
            String sql = MariaDBQueryBuilder.buildCheckUserExistsQuery();
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username, host);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            log.error("Database error checking user existence: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error checking user existence: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== Permission Query Methods ====================

    /**
     * List all grants for a specific user.
     * @param username The username
     * @param host The host pattern
     * @return List of GRANT statements
     * @throws MariaDBValidationException if validation fails
     * @throws MariaDBUserNotFoundException if user doesn't exist
     * @throws MariaDBOperationException if query fails
     */
    public List<String> listUserPermissions(String username, String host) {
        MariaDBValidator.validateUsername(username);
        MariaDBValidator.validateHost(host);

        log.debug("Listing permissions for MariaDB user '{}' @'{}'", username, host);

        try {
            // Check if user exists
            if (!userExists(username, host)) {
                throw new MariaDBUserNotFoundException(username, host);
            }

            // Show grants
            String sql = MariaDBQueryBuilder.buildShowGrantsQuery(username, host);
            return jdbcTemplate.query(sql, (rs, rowNum) ->
                rs.getString(1)  // SHOW GRANTS returns single column
            );

        } catch (MariaDBValidationException | MariaDBUserNotFoundException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Database error listing permissions: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to list user permissions", e);
        } catch (Exception e) {
            log.error("Unexpected error listing permissions: {}", e.getMessage(), e);
            throw new MariaDBOperationException("Failed to list user permissions", e);
        }
    }

    /**
     * Check if a resource (database or table) exists.
     * @param resourceName The resource name (database or database.table)
     * @return true if resource exists, false otherwise
     * @throws MariaDBValidationException if validation fails
     */
    public boolean resourceExists(String resourceName) {
        MariaDBValidator.validateResourceName(resourceName);

        // Special case: wildcard always "exists"
        if ("*".equals(resourceName)) {
            return true;
        }

        try {
            if (resourceName.contains(".")) {
                // Table reference
                String[] parts = resourceName.split("\\.", 2);
                if ("*".equals(parts[1])) {
                    // database.* format - just check database
                    String sql = MariaDBQueryBuilder.buildCheckDatabaseExistsQuery();
                    List<String> results = jdbcTemplate.query(sql,
                        (rs, rowNum) -> rs.getString(1), parts[0]);
                    return !results.isEmpty();
                } else {
                    // database.table format - check both
                    String sql = MariaDBQueryBuilder.buildCheckTableExistsQuery();
                    List<String> results = jdbcTemplate.query(sql,
                        (rs, rowNum) -> rs.getString(1), parts[0], parts[1]);
                    return !results.isEmpty();
                }
            } else {
                // Database reference
                String sql = MariaDBQueryBuilder.buildCheckDatabaseExistsQuery();
                List<String> results = jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString(1), resourceName);
                return !results.isEmpty();
            }
        } catch (DataAccessException e) {
            log.error("Database error checking resource existence: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error checking resource existence: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== Event Scheduler Management ====================

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
