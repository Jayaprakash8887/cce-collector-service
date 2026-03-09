package org.openphc.cce.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.domain.model.InboundEvent;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Enriches the {@link InboundEvent} entity with server-side defaults for
 * optional fields by reading from the inbound request.
 *
 * <p>This is <strong>not</strong> normalization — source-provided values are
 * never modified. Only <em>absent</em> optional fields are filled on the
 * entity:</p>
 *
 * <ol>
 *   <li>{@code correlationId} — generated as {@code "corr-" + UUID} if absent</li>
 *   <li>{@code eventTime} — filled with the server's {@code receivedAt} timestamp if absent</li>
 * </ol>
 *
 * <p>The request DTO is treated as <strong>read-only</strong> — it is never
 * mutated. The entity is the sole source of truth for enriched values.</p>
 *
 * <p>The event {@code type} field is <strong>never</strong> modified —
 * it passes through unchanged from the source system.</p>
 */
@Slf4j
@Service
public class EventDefaultsEnricher {

    private static final String CORRELATION_ID_PREFIX = "corr-";

    /**
     * Enrich the entity with server-side defaults based on the request.
     *
     * <p>Reads optional fields from the {@code request} and sets
     * corresponding values on the {@code inbound} entity. The request
     * DTO is never mutated.</p>
     *
     * @param request the inbound event request (read-only)
     * @param inbound the JPA entity to enrich for persistence
     */
    public void enrich(EventIngestionRequest request, InboundEvent inbound) {
        enrichCorrelationId(request, inbound);
        enrichTime(request, inbound);
    }

    /**
     * Generate a correlation ID if absent: {@code "corr-" + UUID}.
     */
    private void enrichCorrelationId(EventIngestionRequest request, InboundEvent inbound) {
        if (isBlank(request.getCorrelationid())) {
            String generated = CORRELATION_ID_PREFIX + UUID.randomUUID();
            inbound.setCorrelationId(generated);
            log.debug("Generated correlationId: {}", generated);
        } else {
            inbound.setCorrelationId(request.getCorrelationid());
        }
    }

    /**
     * Fill {@code eventTime} with the server's {@code receivedAt} if absent.
     */
    private void enrichTime(EventIngestionRequest request, InboundEvent inbound) {
        if (isBlank(request.getTime())) {
            inbound.setEventTime(inbound.getReceivedAt());
            log.debug("Filled eventTime from server receivedAt: {}", inbound.getReceivedAt());
        } else {
            inbound.setEventTime(OffsetDateTime.parse(request.getTime()));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
