package com.delta.dms.ops.dbaccess.exception;

/**
 * Base exception for all MariaDB database operations.
 * Provides context about whether the operation is retryable and includes error codes.
 */
public class MariaDBOperationException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public MariaDBOperationException(String message) {
        super(message);
        this.errorCode = "MARIADB_ERROR";
        this.retryable = false;
    }

    public MariaDBOperationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MARIADB_ERROR";
        this.retryable = false;
    }

    public MariaDBOperationException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public MariaDBOperationException(String message, Throwable cause, String errorCode, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public String toString() {
        return String.format("MariaDBOperationException[errorCode=%s, retryable=%s, message=%s]",
            errorCode, retryable, getMessage());
    }
}
