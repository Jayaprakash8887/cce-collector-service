package org.openphc.cce.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.springframework.stereotype.Service;

/**
 * Publishes the enriched {@link EventIngestionRequest} to Kafka.
 *
 * <p>The enriched request is published directly — no entity-to-DTO conversion
 * is needed because the request already contains all CloudEvents fields in
 * lowercase, enriched with server-side defaults (correlationid, time).</p>
 *
 * <p><strong>C9 stub:</strong> Actual Kafka publishing via
 * {@code InboundEventProducer} will be wired in C10. Until then,
 * {@link #publish(EventIngestionRequest)} logs the event.</p>
 *
 * <p>The Kafka message key is {@code subject} (patient UPID), ensuring
 * per-patient ordering within a partition.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    /**
     * Publish an enriched event request to Kafka.
     *
     * <p>Sends the enriched request synchronously to the configured inbound
     * topic. Blocks until the broker acknowledges the write.</p>
     *
     * <p><strong>Note:</strong> Actual Kafka send via {@code InboundEventProducer}
     * will be added in C10. This stub logs the event.</p>
     *
     * @param request the enriched event ingestion request
     * @throws KafkaPublishException if publishing fails
     */
    public void publish(EventIngestionRequest request) {
        log.info("Event ready for Kafka publish: cloudeventsId={}, subject={}, topic=cce.events.inbound",
                request.getId(), request.getSubject());
        // C10 will inject InboundEventProducer and call: producer.publish(request)
    }
}
