package com.delta.dms.ops.dbaccess.exception;

/**
 * Exception thrown when validation of MariaDB operation parameters fails.
 * This exception is never retryable as invalid input won't become valid on retry.
 */
public class MariaDBValidationException extends MariaDBOperationException {

    public MariaDBValidationException(String message) {
        super(message, "VALIDATION_ERROR", false);  // Never retryable
    }

    public MariaDBValidationException(String message, Throwable cause) {
        super(message, cause, "VALIDATION_ERROR", false);
    }

    /**
     * Create exception for invalid identifier.
     */
    public static MariaDBValidationException invalidIdentifier(String identifier, String reason) {
        return new MariaDBValidationException(
            String.format("Invalid identifier '%s': %s", identifier, reason)
        );
    }

    /**
     * Create exception for invalid resource name.
     */
    public static MariaDBValidationException invalidResource(String resourceName, String reason) {
        return new MariaDBValidationException(
            String.format("Invalid resource name '%s': %s", resourceName, reason)
        );
    }

    /**
     * Create exception for invalid time range.
     */
    public static MariaDBValidationException invalidTimeRange(String reason) {
        return new MariaDBValidationException(
            String.format("Invalid time range: %s", reason)
        );
    }

    /**
     * Create exception for missing required parameter.
     */
    public static MariaDBValidationException missingParameter(String parameterName) {
        return new MariaDBValidationException(
            String.format("Required parameter '%s' is null or empty", parameterName)
        );
    }
}
