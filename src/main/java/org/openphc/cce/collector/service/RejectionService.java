package org.openphc.cce.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.domain.model.InboundEvent;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;
import org.openphc.cce.collector.domain.repository.InboundEventRepository;
import org.springframework.stereotype.Service;

/**
 * Records rejection details on an {@link InboundEvent}.
 *
 * <p>Rejection is tracked directly on the {@code inbound_event} table —
 * no separate {@code dead_letter_event} table exists. This service sets
 * the status to {@link InboundStatus#REJECTED}, records the
 * {@link RejectionReason}, and persists any error detail text.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RejectionService {

    private final InboundEventRepository repository;

    /**
     * Mark an inbound event as rejected and persist the rejection metadata.
     *
     * @param event        the event to reject (must already be persisted)
     * @param reason       the machine-readable rejection reason
     * @param errorDetails human-readable error description (may be truncated
     *                     to fit the {@code text} column)
     */
    public void recordRejection(InboundEvent event, RejectionReason reason, String errorDetails) {
        event.setStatus(InboundStatus.REJECTED);
        event.setRejectionReason(reason.name());
        event.setErrorDetails(errorDetails);
        repository.save(event);

        log.warn("Event rejected: id={}, cloudeventsId={}, reason={}, details={}",
                event.getId(), event.getCloudeventsId(), reason, errorDetails);
    }
}
