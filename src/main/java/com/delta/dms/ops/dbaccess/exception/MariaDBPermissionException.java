package com.delta.dms.ops.dbaccess.exception;

/**
 * Exception thrown when MariaDB permission operations (GRANT/REVOKE) fail.
 * Can be retryable depending on the cause (connection issues vs permission denied).
 */
public class MariaDBPermissionException extends MariaDBOperationException {

    public MariaDBPermissionException(String message) {
        super(message, "PERMISSION_ERROR", false);
    }

    public MariaDBPermissionException(String message, Throwable cause) {
        super(message, cause, "PERMISSION_ERROR", false);
    }

    public MariaDBPermissionException(String message, Throwable cause, boolean retryable) {
        super(message, cause, "PERMISSION_ERROR", retryable);
    }

    public MariaDBPermissionException(String message, boolean retryable) {
        super(message, "PERMISSION_ERROR", retryable);
    }

    /**
     * Create exception for failed grant operation.
     */
    public static MariaDBPermissionException grantFailed(String username, String resource, Throwable cause) {
        return new MariaDBPermissionException(
            String.format("Failed to grant permission on '%s' to user '%s'", resource, username),
            cause,
            isTransientError(cause)
        );
    }

    /**
     * Create exception for failed revoke operation.
     */
    public static MariaDBPermissionException revokeFailed(String username, String resource, Throwable cause) {
        return new MariaDBPermissionException(
            String.format("Failed to revoke permission on '%s' from user '%s'", resource, username),
            cause,
            isTransientError(cause)
        );
    }

    /**
     * Determine if the error is transient (retryable).
     */
    private static boolean isTransientError(Throwable cause) {
        if (cause == null) {
            return false;
        }

        String message = cause.getMessage();
        if (message == null) {
            return false;
        }

        // Common transient error patterns in MariaDB
        return message.contains("Connection refused") ||
               message.contains("Communications link failure") ||
               message.contains("Timeout") ||
               message.contains("Lock wait timeout") ||
               message.contains("Deadlock");
    }
}
