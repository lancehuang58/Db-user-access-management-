package com.delta.dms.ops.dbaccess.util;

import com.delta.dms.ops.dbaccess.exception.MariaDBValidationException;
import com.delta.dms.ops.dbaccess.model.Permission;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Utility class for validating MariaDB operation inputs.
 * Provides comprehensive validation for usernames, hosts, resource names, and time ranges.
 */
@Slf4j
public class MariaDBValidator {

    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final int MAX_USERNAME_LENGTH = 32;  // MariaDB username limit
    private static final int MAX_PASSWORD_LENGTH = 256;

    // Pattern for valid MariaDB usernames (alphanumeric, underscore, dollar, dot)
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_$.]+$");

    // Pattern for valid database/table names
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9_$]+$");

    // Pattern for valid host (%, localhost, IP, hostname)
    private static final Pattern HOST_PATTERN = Pattern.compile("^[%a-zA-Z0-9._-]+$");

    /**
     * Validate username.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw MariaDBValidationException.missingParameter("username");
        }

        if (username.length() > MAX_USERNAME_LENGTH) {
            throw MariaDBValidationException.invalidIdentifier(username,
                String.format("exceeds maximum length of %d characters", MAX_USERNAME_LENGTH));
        }

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw MariaDBValidationException.invalidIdentifier(username,
                "contains invalid characters. Only alphanumeric, underscore, dot, and dollar sign are allowed");
        }

        // Username cannot start with a digit (but can start with a dot for special accounts)
        if (Character.isDigit(username.charAt(0))) {
            throw MariaDBValidationException.invalidIdentifier(username,
                "cannot start with a digit");
        }

        // Warn about common system users
        if (username.equalsIgnoreCase("root") ||
            username.equalsIgnoreCase("mysql") ||
            username.startsWith("mysql.")) {
            log.warn("Username '{}' matches a system user pattern", username);
        }
    }

    /**
     * Validate host pattern.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateHost(String host) {
        if (host == null || host.isEmpty()) {
            throw MariaDBValidationException.missingParameter("host");
        }

        if (host.length() > MAX_IDENTIFIER_LENGTH) {
            throw new MariaDBValidationException(
                String.format("Host '%s' exceeds maximum length of %d characters", host, MAX_IDENTIFIER_LENGTH)
            );
        }

        if (!HOST_PATTERN.matcher(host).matches()) {
            throw new MariaDBValidationException(
                String.format("Invalid host pattern '%s'. Allowed characters: alphanumeric, dot, hyphen, underscore, percent", host)
            );
        }
    }

    /**
     * Validate database name.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isEmpty()) {
            throw MariaDBValidationException.missingParameter("databaseName");
        }

        if (databaseName.length() > MAX_IDENTIFIER_LENGTH) {
            throw MariaDBValidationException.invalidIdentifier(databaseName,
                String.format("exceeds maximum length of %d characters", MAX_IDENTIFIER_LENGTH));
        }

        if (!IDENTIFIER_PATTERN.matcher(databaseName).matches()) {
            throw MariaDBValidationException.invalidIdentifier(databaseName,
                "contains invalid characters. Only alphanumeric, underscore, and dollar sign are allowed");
        }

        // Database name cannot start with a digit
        if (Character.isDigit(databaseName.charAt(0))) {
            throw MariaDBValidationException.invalidIdentifier(databaseName,
                "cannot start with a digit");
        }

        // Warn about system databases
        if (databaseName.equalsIgnoreCase("mysql") ||
            databaseName.equalsIgnoreCase("information_schema") ||
            databaseName.equalsIgnoreCase("performance_schema") ||
            databaseName.equalsIgnoreCase("sys")) {
            log.warn("Database name '{}' is a system database", databaseName);
        }
    }

    /**
     * Validate table name.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw MariaDBValidationException.missingParameter("tableName");
        }

        if (tableName.length() > MAX_IDENTIFIER_LENGTH) {
            throw MariaDBValidationException.invalidIdentifier(tableName,
                String.format("exceeds maximum length of %d characters", MAX_IDENTIFIER_LENGTH));
        }

        if (!IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw MariaDBValidationException.invalidIdentifier(tableName,
                "contains invalid characters. Only alphanumeric, underscore, and dollar sign are allowed");
        }

        // Table name cannot start with a digit
        if (Character.isDigit(tableName.charAt(0))) {
            throw MariaDBValidationException.invalidIdentifier(tableName,
                "cannot start with a digit");
        }
    }

    /**
     * Validate resource name (database or database.table format).
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateResourceName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            throw MariaDBValidationException.missingParameter("resourceName");
        }

        // Special case: wildcard for all databases
        if ("*".equals(resourceName)) {
            return;
        }

        // Check if it's a table reference (database.table)
        if (resourceName.contains(".")) {
            String[] parts = resourceName.split("\\.", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw MariaDBValidationException.invalidResource(resourceName,
                    "invalid format. Expected 'database.table'");
            }

            // Handle wildcard in table name (database.*)
            if (!"*".equals(parts[1])) {
                validateDatabaseName(parts[0]);
                validateTableName(parts[1]);
            } else {
                validateDatabaseName(parts[0]);
            }
        } else {
            // Single database name
            validateDatabaseName(resourceName);
        }
    }

    /**
     * Validate password meets security requirements.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw MariaDBValidationException.missingParameter("password");
        }

        if (password.length() < 8) {
            throw new MariaDBValidationException(
                "Password must be at least 8 characters long"
            );
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new MariaDBValidationException(
                String.format("Password exceeds maximum length of %d characters", MAX_PASSWORD_LENGTH)
            );
        }

        // Check for minimum complexity (at least one letter and one digit)
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);

        if (!hasLetter || !hasDigit) {
            throw new MariaDBValidationException(
                "Password must contain at least one letter and one digit"
            );
        }
    }

    /**
     * Validate permission time range.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null) {
            throw MariaDBValidationException.missingParameter("startTime");
        }

        if (endTime == null) {
            throw MariaDBValidationException.missingParameter("endTime");
        }

        LocalDateTime now = LocalDateTime.now();

        // End time must be after start time
        if (!endTime.isAfter(startTime)) {
            throw MariaDBValidationException.invalidTimeRange(
                "end time must be after start time"
            );
        }

        // Warn if start time is in the past
        if (startTime.isBefore(now)) {
            log.warn("Permission start time {} is in the past", startTime);
        }

        // End time should not be in the past
        if (endTime.isBefore(now)) {
            throw MariaDBValidationException.invalidTimeRange(
                "end time cannot be in the past"
            );
        }

        // Warn if permission duration is very long (more than 1 year)
        if (startTime.plusYears(1).isBefore(endTime)) {
            log.warn("Permission duration exceeds 1 year. Start: {}, End: {}", startTime, endTime);
        }

        // Warn if permission duration is very short (less than 1 hour)
        if (startTime.plusHours(1).isAfter(endTime)) {
            log.warn("Permission duration is less than 1 hour. Start: {}, End: {}", startTime, endTime);
        }
    }

    /**
     * Validate permission object.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validatePermission(Permission permission) {
        if (permission == null) {
            throw MariaDBValidationException.missingParameter("permission");
        }

        if (permission.getMariadbUsername() == null || permission.getMariadbUsername().isEmpty()) {
            throw MariaDBValidationException.missingParameter("permission.mariadbUsername");
        }

        if (permission.getMariadbHost() == null || permission.getMariadbHost().isEmpty()) {
            throw MariaDBValidationException.missingParameter("permission.mariadbHost");
        }

        validateUsername(permission.getMariadbUsername());
        validateHost(permission.getMariadbHost());
        validateResourceName(permission.getResourceName());
        validateTimeRange(permission.getStartTime(), permission.getEndTime());

        if (permission.getType() == null) {
            throw MariaDBValidationException.missingParameter("permission.type");
        }
    }

    /**
     * Validate event name for MariaDB event scheduler.
     * @throws MariaDBValidationException if validation fails
     */
    public static void validateEventName(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            throw MariaDBValidationException.missingParameter("eventName");
        }

        if (eventName.length() > MAX_IDENTIFIER_LENGTH) {
            throw MariaDBValidationException.invalidIdentifier(eventName,
                String.format("exceeds maximum length of %d characters", MAX_IDENTIFIER_LENGTH));
        }

        if (!IDENTIFIER_PATTERN.matcher(eventName).matches()) {
            throw MariaDBValidationException.invalidIdentifier(eventName,
                "contains invalid characters. Only alphanumeric, underscore, and dollar sign are allowed");
        }
    }
}
