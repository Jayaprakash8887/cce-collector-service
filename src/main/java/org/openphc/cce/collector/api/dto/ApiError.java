package org.openphc.cce.collector.api.dto;

/**
 * Standard error response envelope: {@code {"error": {"code": "...", "message": "..."}}}.
 *
 * <p>All error API responses are wrapped in this envelope
 * per the CCE Design standard.</p>
 *
 * @param error the error body containing code and message
 */
public record ApiError(ErrorBody error) {

    /**
     * Error body with a machine-readable code and human-readable message.
     *
     * @param code    machine-readable error code (e.g. {@code VALIDATION_ERROR})
     * @param message human-readable description
     */
    public record ErrorBody(String code, String message) {
    }

    /**
     * Factory method for convenience.
     *
     * @param code    machine-readable error code
     * @param message human-readable description
     * @return wrapped error response
     */
    public static ApiError of(String code, String message) {
        return new ApiError(new ErrorBody(code, message));
    }
}
