package org.openphc.cce.collector.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO for rejected event API responses ({@code GET /v1/events/rejected}).
 *
 * <p>Projected from {@code inbound_event} where {@code status = 'REJECTED'}.
 * Uses camelCase field names — this is CCE's own API response format.</p>
 *
 * <p><strong>NOTE:</strong> No {@code failureStage} field (derivable from
 * {@code rejectionReason}). No {@code resolved} or {@code resolvedAt} fields
 * (removed from schema).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RejectedEventDto {

    /** UUIDv7 primary key of the {@code inbound_event} record. */
    private String id;

    /** Original CloudEvents {@code id} from the source system. */
    private String cloudEventsId;

    /** Event source identifier. */
    private String source;

    /** CloudEvents {@code type} value. */
    private String eventType;

    /** Rejection reason code (e.g. {@code INVALID_FHIR}, {@code INVALID_ENVELOPE}). */
    private String rejectionReason;

    /** Validation error messages or stack trace. */
    private String errorDetails;

    /** Server-side receipt timestamp (UTC). */
    private OffsetDateTime receivedAt;
}
