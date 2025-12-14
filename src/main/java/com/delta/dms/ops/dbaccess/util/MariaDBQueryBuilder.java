package com.delta.dms.ops.dbaccess.util;

import com.delta.dms.ops.dbaccess.model.Permission;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for building safe MariaDB SQL queries.
 * Provides proper identifier quoting and string literal escaping to prevent SQL injection.
 */
@Slf4j
public class MariaDBQueryBuilder {

    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // MariaDB reserved keywords (partial list - most commonly used)
    private static final Set<String> RESERVED_KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "TABLE",
        "DATABASE", "INDEX", "VIEW", "TRIGGER", "PROCEDURE", "FUNCTION", "USER",
        "FROM", "WHERE", "GROUP", "ORDER", "BY", "HAVING", "JOIN", "INNER", "OUTER",
        "LEFT", "RIGHT", "ON", "AS", "AND", "OR", "NOT", "NULL", "TRUE", "FALSE",
        "GRANT", "REVOKE", "PRIVILEGES", "ALL", "EVENT", "SCHEDULE", "BEGIN", "END"
    ));

    public enum ResourceType {
        GLOBAL,      // *.*
        DATABASE,    // database.*
        TABLE        // database.table
    }

    /**
     * Quote an identifier with backticks for safe SQL usage.
     * Handles escaping of backticks within the identifier.
     */
    public static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Identifier '%s' exceeds maximum length of %d characters",
                    identifier, MAX_IDENTIFIER_LENGTH)
            );
        }

        // Escape any backticks in the identifier by doubling them
        String escaped = identifier.replace("`", "``");
        return "`" + escaped + "`";
    }

    /**
     * Escape a string literal for safe SQL usage.
     * This returns the value WITHOUT surrounding quotes - suitable for PreparedStatement parameters.
     */
    public static String escapeStringLiteral(String value) {
        if (value == null) {
            return null;
        }

        // Escape single quotes and backslashes for MariaDB
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * Quote a string literal with single quotes for direct SQL usage.
     * WARNING: Prefer using PreparedStatement with parameters when possible.
     */
    public static String quoteStringLiteral(String value) {
        if (value == null) {
            return "NULL";
        }

        return "'" + escapeStringLiteral(value) + "'";
    }

    /**
     * Validate identifier against MariaDB naming rules.
     * @param identifier The identifier to validate
     * @param allowDot Whether to allow dots (for database.table format)
     */
    public static void validateIdentifier(String identifier, boolean allowDot) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Identifier '%s' exceeds maximum length of %d characters",
                    identifier, MAX_IDENTIFIER_LENGTH)
            );
        }

        // Check for reserved keywords
        String upperIdentifier = identifier.toUpperCase();
        if (RESERVED_KEYWORDS.contains(upperIdentifier)) {
            log.warn("Identifier '{}' is a reserved keyword and will be quoted", identifier);
        }

        // Validate characters (alphanumeric, underscore, dollar, and optionally dot)
        String pattern = allowDot ? "^[a-zA-Z0-9_$.]+$" : "^[a-zA-Z0-9_$]+$";
        if (!identifier.matches(pattern)) {
            throw new IllegalArgumentException(
                String.format("Identifier '%s' contains invalid characters. Only alphanumeric, underscore, and dollar sign are allowed%s",
                    identifier, allowDot ? " (and dot for qualified names)" : "")
            );
        }

        // MariaDB identifiers cannot start with a digit (unless quoted, but we validate anyway)
        if (Character.isDigit(identifier.charAt(0))) {
            throw new IllegalArgumentException(
                String.format("Identifier '%s' cannot start with a digit", identifier)
            );
        }
    }

    /**
     * Build a CREATE USER statement.
     * Returns SQL with placeholder for password to be used with PreparedStatement.
     */
    public static String buildCreateUser(String username, String host) {
        validateIdentifier(username, false);

        // Host can be %, localhost, IP address, or hostname pattern
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        // Build: CREATE USER IF NOT EXISTS 'username'@'host' IDENTIFIED BY ?
        return String.format(
            "CREATE USER IF NOT EXISTS %s@%s IDENTIFIED BY ?",
            quoteStringLiteral(username),  // Username is a string literal, not identifier
            quoteStringLiteral(host)
        );
    }

    /**
     * Build a DROP USER statement.
     */
    public static String buildDropUser(String username, String host) {
        validateIdentifier(username, false);

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        return String.format(
            "DROP USER IF EXISTS %s@%s",
            quoteStringLiteral(username),
            quoteStringLiteral(host)
        );
    }

    /**
     * Build an ALTER USER statement for password change.
     * Returns SQL with placeholder for new password.
     */
    public static String buildAlterUserPassword(String username, String host) {
        validateIdentifier(username, false);

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        return String.format(
            "ALTER USER %s@%s IDENTIFIED BY ?",
            quoteStringLiteral(username),
            quoteStringLiteral(host)
        );
    }

    /**
     * Build a GRANT statement.
     */
    public static String buildGrantStatement(List<String> privileges, ResourceType resourceType,
                                             String resourceName, String username, String host) {
        if (privileges == null || privileges.isEmpty()) {
            throw new IllegalArgumentException("Privileges list cannot be null or empty");
        }

        validateIdentifier(username, false);

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        String privList = String.join(", ", privileges);
        String resource = buildResourceSpecifier(resourceType, resourceName);

        return String.format(
            "GRANT %s ON %s TO %s@%s",
            privList,
            resource,
            quoteStringLiteral(username),
            quoteStringLiteral(host)
        );
    }

    /**
     * Build a REVOKE statement.
     */
    public static String buildRevokeStatement(List<String> privileges, ResourceType resourceType,
                                              String resourceName, String username, String host) {
        if (privileges == null || privileges.isEmpty()) {
            throw new IllegalArgumentException("Privileges list cannot be null or empty");
        }

        validateIdentifier(username, false);

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        String privList = String.join(", ", privileges);
        String resource = buildResourceSpecifier(resourceType, resourceName);

        return String.format(
            "REVOKE %s ON %s FROM %s@%s",
            privList,
            resource,
            quoteStringLiteral(username),
            quoteStringLiteral(host)
        );
    }

    /**
     * Build resource specifier for GRANT/REVOKE statements.
     */
    private static String buildResourceSpecifier(ResourceType resourceType, String resourceName) {
        switch (resourceType) {
            case GLOBAL:
                return "*.*";

            case DATABASE:
                validateIdentifier(resourceName, false);
                return quoteIdentifier(resourceName) + ".*";

            case TABLE:
                // Resource name should be in format: database.table
                if (resourceName == null || !resourceName.contains(".")) {
                    throw new IllegalArgumentException(
                        "Table resource must be in format 'database.table'"
                    );
                }

                String[] parts = resourceName.split("\\.", 2);
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    throw new IllegalArgumentException(
                        "Invalid table resource format. Expected 'database.table'"
                    );
                }

                validateIdentifier(parts[0], false);  // database name
                validateIdentifier(parts[1], false);  // table name

                return quoteIdentifier(parts[0]) + "." + quoteIdentifier(parts[1]);

            default:
                throw new IllegalArgumentException("Unknown resource type: " + resourceType);
        }
    }

    /**
     * Determine resource type from resource name.
     */
    public static ResourceType determineResourceType(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            throw new IllegalArgumentException("Resource name cannot be null or empty");
        }

        if ("*".equals(resourceName)) {
            return ResourceType.GLOBAL;
        } else if (resourceName.contains(".")) {
            return ResourceType.TABLE;
        } else {
            return ResourceType.DATABASE;
        }
    }

    /**
     * Build CREATE EVENT statement for auto-revoke functionality.
     */
    public static String buildCreateRevokeEvent(String eventName, LocalDateTime scheduleTime,
                                               List<String> privileges, ResourceType resourceType,
                                               String resourceName, String username, String host) {
        validateIdentifier(eventName, false);
        validateIdentifier(username, false);

        if (scheduleTime == null) {
            throw new IllegalArgumentException("Schedule time cannot be null");
        }

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        String revokeStatement = buildRevokeStatement(privileges, resourceType, resourceName, username, host);
        String scheduleTimeStr = scheduleTime.format(DATETIME_FORMATTER);

        // Build event creation SQL
        return String.format(
            "CREATE EVENT %s " +
            "ON SCHEDULE AT %s " +
            "DO BEGIN " +
            "  %s; " +
            "END",
            quoteIdentifier(eventName),
            quoteStringLiteral(scheduleTimeStr),
            revokeStatement
        );
    }

    /**
     * Build DROP EVENT statement.
     */
    public static String buildDropEvent(String eventName) {
        validateIdentifier(eventName, false);
        return String.format("DROP EVENT IF EXISTS %s", quoteIdentifier(eventName));
    }

    /**
     * Get MariaDB privileges list based on permission type.
     */
    public static List<String> getPrivilegesForType(Permission.PermissionType type) {
        return switch (type) {
            case READ -> List.of("SELECT");
            case WRITE -> List.of("SELECT", "INSERT", "UPDATE");
            case DELETE -> List.of("SELECT", "DELETE");
            case EXECUTE -> List.of("EXECUTE");
            case ADMIN -> List.of("ALL PRIVILEGES");
        };
    }

    /**
     * Build query to check if user exists.
     */
    public static String buildCheckUserExistsQuery() {
        return "SELECT COUNT(*) FROM mysql.user WHERE user = ? AND host = ?";
    }

    /**
     * Build query to list all users.
     */
    public static String buildListUsersQuery() {
        return "SELECT user, host, account_locked, password_expired FROM mysql.user " +
               "WHERE user NOT IN ('root', 'mysql.sys', 'mysql.session', 'mysql.infoschema') " +
               "ORDER BY user, host";
    }

    /**
     * Build query to get user information.
     */
    public static String buildGetUserInfoQuery() {
        return "SELECT user, host, account_locked, password_expired FROM mysql.user " +
               "WHERE user = ? AND host = ?";
    }

    /**
     * Build query to show grants for a user.
     */
    public static String buildShowGrantsQuery(String username, String host) {
        validateIdentifier(username, false);

        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }

        return String.format(
            "SHOW GRANTS FOR %s@%s",
            quoteStringLiteral(username),
            quoteStringLiteral(host)
        );
    }

    /**
     * Build query to check if database exists.
     */
    public static String buildCheckDatabaseExistsQuery() {
        return "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
    }

    /**
     * Build query to check if table exists.
     */
    public static String buildCheckTableExistsQuery() {
        return "SELECT TABLE_NAME FROM information_schema.TABLES " +
               "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
    }
}
