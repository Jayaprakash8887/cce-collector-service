package org.openphc.cce.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.domain.model.InboundEventLog;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Enriches <strong>both</strong> the {@link InboundEventLog} entity and the
 * {@link EventIngestionRequest} DTO with server-side defaults for optional
 * fields.
 *
 * <p>This is <strong>not</strong> normalization — source-provided values are
 * never modified. Only <em>absent</em> optional fields are filled:</p>
 *
 * <ol>
 *   <li>{@code correlationId / correlationid} — generated as {@code "corr-" + UUID} if absent</li>
 *   <li>{@code time} — filled with the server's {@code receivedAt} timestamp if absent (on request DTO only, for Kafka publishing)</li>
 * </ol>
 *
 * <p>The request DTO is also updated so that when it is published to Kafka
 * it carries the enriched values. The entity remains the authoritative
 * persistence record.</p>
 *
 * <p>The event {@code type} field is <strong>never</strong> modified —
 * it passes through unchanged from the source system.</p>
 */
@Slf4j
@Service
public class EventDefaultsEnricher {

    private static final String CORRELATION_ID_PREFIX = "corr-";

    /**
     * Enrich both the entity and the request with server-side defaults.
     *
     * <p>Reads optional fields from the {@code request}. When a field is
     * absent, generates a default and sets it on <strong>both</strong> the
     * entity (for persistence) and the request (for Kafka publishing).</p>
     *
     * @param request the inbound event request (mutated with defaults)
     * @param inbound the JPA entity to enrich for persistence
     */
    public void enrich(EventIngestionRequest request, InboundEventLog inbound) {
        enrichCorrelationId(request, inbound);
        enrichTime(request, inbound);
    }

    /**
     * Generate a correlation ID if absent: {@code "corr-" + UUID}.
     * Sets the value on both entity and request.
     */
    private void enrichCorrelationId(EventIngestionRequest request, InboundEventLog inbound) {
        if (isBlank(request.getCorrelationid())) {
            String generated = CORRELATION_ID_PREFIX + UUID.randomUUID();
            inbound.setCorrelationId(generated);
            request.setCorrelationid(generated);
            log.debug("Generated correlationId: {}", generated);
        } else {
            inbound.setCorrelationId(request.getCorrelationid());
        }
    }

    /**
     * Fill {@code time} on the request DTO with the server's {@code receivedAt} if absent.
     * Only enriches the request (for Kafka publishing) — no entity field to set.
     */
    private void enrichTime(EventIngestionRequest request, InboundEventLog inbound) {
        if (isBlank(request.getTime())) {
            OffsetDateTime receivedAt = inbound.getReceivedAt();
            request.setTime(receivedAt.toString());
            log.debug("Filled time from server receivedAt: {}", receivedAt);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
