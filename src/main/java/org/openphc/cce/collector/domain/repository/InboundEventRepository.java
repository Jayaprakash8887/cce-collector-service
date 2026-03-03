package org.openphc.cce.collector.domain.repository;

import org.openphc.cce.collector.domain.model.InboundEvent;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InboundEvent}.
 *
 * <p>All rejected-event queries operate on this single table —
 * no separate dead_letter_event table exists.</p>
 */
@Repository
public interface InboundEventRepository extends JpaRepository<InboundEvent, UUID> {

    /**
     * Find an event by its CloudEvents id and source (exact match).
     *
     * <p>Used for entity retrieval — returning the existing {@code eventId} in the
     * idempotent 200 duplicate response, and for the rejected-event retry endpoint
     * ({@code POST /v1/events/rejected/{id}/retry}) which re-runs validation on
     * the original {@code raw_payload}.</p>
     *
     * <p><strong>Not</strong> the dedup decision method — see
     * {@link #existsByCloudeventsIdAndSourceAndReceivedAtAfter}.</p>
     *
     * @param cloudeventsId the CloudEvents id
     * @param source        the event source
     * @return the matching event, if any
     */
    Optional<InboundEvent> findByCloudeventsIdAndSource(String cloudeventsId, String source);

    /**
     * Check if a duplicate event exists within the lookback window.
     *
     * <p>This is the <strong>primary dedup decision</strong> used during event
     * ingestion (Step 3 of the processing pipeline). Returns a lightweight
     * boolean — no entity hydration — keeping the hot path fast.</p>
     *
     * @param cloudeventsId the CloudEvents id
     * @param source        the event source
     * @param since         the start of the lookback window (e.g. {@code now() − 30 days})
     * @return true if a matching event exists after the given date
     */
    boolean existsByCloudeventsIdAndSourceAndReceivedAtAfter(
            String cloudeventsId, String source, OffsetDateTime since);

    /**
     * Find events by status, ordered by most recent first.
     * Used by the rejected-event management endpoints.
     *
     * @param status   the status to filter by (typically REJECTED)
     * @param pageable pagination parameters
     * @return paginated list of events with the given status
     */
    Page<InboundEvent> findByStatusOrderByReceivedAtDesc(
            InboundStatus status, Pageable pageable);

    /**
     * Count events by status.
     * Used for the rejected gauge metric.
     *
     * @param status the status to count
     * @return count of matching events
     */
    long countByStatus(InboundStatus status);
}
