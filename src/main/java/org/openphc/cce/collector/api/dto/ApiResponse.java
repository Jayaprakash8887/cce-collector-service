package org.openphc.cce.collector.api.dto;

/**
 * Standard success response envelope: {@code {"data": ...}}.
 *
 * <p>All successful API responses are wrapped in this envelope
 * per the CCE Design standard. The payload is always an
 * {@link EventIngestionResponse}.</p>
 *
 * @param data the {@link EventIngestionResponse} payload
 */
public record ApiResponse(EventIngestionResponse data) {

    /**
     * Factory method for convenience.
     *
     * @param data the {@link EventIngestionResponse} payload
     * @return wrapped response
     */
    public static ApiResponse of(EventIngestionResponse data) {
        return new ApiResponse(data);
    }
}
