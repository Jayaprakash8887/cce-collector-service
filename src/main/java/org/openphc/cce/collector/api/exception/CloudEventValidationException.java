package org.openphc.cce.collector.api.exception;

import java.util.List;

/**
 * Thrown when CloudEvents envelope validation fails.
 *
 * <p>Contains the complete list of validation errors — all rules are
 * evaluated and every violation is reported (not just the first).</p>
 *
 * <p>Mapped to HTTP 400 Bad Request by {@code GlobalExceptionHandler} (C9).</p>
 */
public class CloudEventValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public CloudEventValidationException(List<String> validationErrors) {
        super("CloudEvents validation failed: " + String.join("; ", validationErrors),
                null, false, false);
        this.validationErrors = List.copyOf(validationErrors);
    }

    /**
     * Returns an unmodifiable list of all validation error messages.
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}
