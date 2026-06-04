package org.openphc.cce.collector.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Response DTO returned after event ingestion.
 *
 * <p>Wrapped in {@link ApiResponse} for the standard {@code {"data": ...}} envelope.
 * Uses camelCase field names — this is CCE's own API response format,
 * not CloudEvents wire format.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventIngestionResponse {

    /** UUIDv7 primary key of the persisted {@code inbound_event_log} record. */
    private String eventId;

    /** Original CloudEvents {@code id} from the source system. */
    private String cloudEventsId;

    /** Processing outcome: {@code "accepted"} or {@code "duplicate"}. */
    private String status;

    /** Correlation ID (source-provided or server-generated {@code corr-<uuid>}). */
    private String correlationId;

    /** Server-side receipt timestamp (UTC). */
    private OffsetDateTime receivedAt;
}
