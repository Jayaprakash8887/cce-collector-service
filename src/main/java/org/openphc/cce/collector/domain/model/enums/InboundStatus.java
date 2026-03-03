package org.openphc.cce.collector.domain.model.enums;

/**
 * Status lifecycle for an inbound event.
 *
 * <ul>
 *   <li>{@code RECEIVED}  — Event persisted but not yet validated</li>
 *   <li>{@code ACCEPTED}  — Passed all validations and published to Kafka</li>
 *   <li>{@code REJECTED}  — Failed validation (see rejection_reason)</li>
 *   <li>{@code DUPLICATE} — Duplicate of an existing event (idempotent)</li>
 * </ul>
 */
public enum InboundStatus {
    RECEIVED,
    ACCEPTED,
    REJECTED,
    DUPLICATE
}
