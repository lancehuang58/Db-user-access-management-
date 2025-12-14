package com.delta.dms.ops.dbaccess.exception;

/**
 * Exception thrown when a MariaDB user is not found.
 * This exception is not retryable as missing users won't appear on retry.
 */
public class MariaDBUserNotFoundException extends MariaDBOperationException {

    public MariaDBUserNotFoundException(String username, String host) {
        super(
            String.format("MariaDB user '%s'@'%s' not found", username, host),
            "USER_NOT_FOUND",
            false  // Not retryable
        );
    }

    public MariaDBUserNotFoundException(String message) {
        super(message, "USER_NOT_FOUND", false);
    }

    public MariaDBUserNotFoundException(String message, Throwable cause) {
        super(message, cause, "USER_NOT_FOUND", false);
    }
}
