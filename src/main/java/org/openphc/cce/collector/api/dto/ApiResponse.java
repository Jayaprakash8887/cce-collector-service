package org.openphc.cce.collector.api.dto;

/**
 * Standard success response envelope: {@code {"data": T}}.
 *
 * <p>All successful API responses are wrapped in this envelope
 * per the CCE Design standard.</p>
 *
 * @param data the response payload
 * @param <T>  payload type
 */
public record ApiResponse<T>(T data) {

    /**
     * Factory method for convenience.
     *
     * @param data the response payload
     * @param <T>  payload type
     * @return wrapped response
     */
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
