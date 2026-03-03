package org.openphc.cce.collector.domain.model.enums;

/**
 * Reasons an inbound event can be rejected.
 *
 * <p>Stored as VARCHAR in the database (not as an enum column) to allow
 * flexibility for future reason codes without schema changes.</p>
 *
 * <p><b>NOTE:</b> No FailureStage enum exists — the failure stage is fully
 * derivable from the rejection reason (e.g. {@code KAFKA_PUBLISH_FAILURE}
 * implies the Kafka publish stage; all others imply validation or processing).</p>
 */
public enum RejectionReason {
    INVALID_ENVELOPE,
    INVALID_FHIR,
    INVALID_JSON,
    UNSUPPORTED_CONTENT_TYPE,
    DUPLICATE,
    MISSING_SUBJECT,
    PAYLOAD_TOO_LARGE,
    DESERIALIZATION_ERROR,
    KAFKA_PUBLISH_FAILURE
}
