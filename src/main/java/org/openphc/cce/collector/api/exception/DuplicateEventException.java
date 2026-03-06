package org.openphc.cce.collector.api.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Thrown when a duplicate event is detected during ingestion.
 *
 * <p>The Collector follows the idempotent POST pattern: duplicate
 * submissions receive HTTP 200 (not 409 Conflict) with the existing
 * event's ID and a {@code "duplicate"} status. This exception carries
 * the information needed to build that idempotent response.</p>
 *
 * @see org.openphc.cce.collector.service.DeduplicationService
 */
@Getter
public class DuplicateEventException extends RuntimeException {

    /**
     * The internal event ID (UUIDv7) assigned to the duplicate submission.
     */
    private final UUID eventId;

    /**
     * The internal event ID of the existing (original) event record.
     */
    private final UUID existingRecordId;

    public DuplicateEventException(UUID eventId, UUID existingRecordId) {
        super("Duplicate event detected: eventId=" + eventId
                + ", existingRecordId=" + existingRecordId);
        this.eventId = eventId;
        this.existingRecordId = existingRecordId;
    }
}
