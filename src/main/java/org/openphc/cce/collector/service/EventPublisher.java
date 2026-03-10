package org.openphc.cce.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.openphc.cce.collector.kafka.InboundEventProducer;
import org.springframework.stereotype.Service;

/**
 * Publishes the enriched {@link EventIngestionRequest} to Kafka.
 *
 * <p>Delegates to {@link InboundEventProducer} for the actual synchronous
 * Kafka send. The enriched request is published directly — no entity-to-DTO
 * conversion is needed because the request already contains all CloudEvents
 * fields in lowercase, enriched with server-side defaults (correlationid,
 * time).</p>
 *
 * <p>The Kafka message key is {@code subject} (patient UPID), ensuring
 * per-patient ordering within a partition.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final InboundEventProducer inboundEventProducer;

    /**
     * Publish an enriched event request to Kafka.
     *
     * <p>Sends the enriched request synchronously to the configured inbound
     * topic. Blocks until the broker acknowledges the write.</p>
     *
     * @param request the enriched event ingestion request
     * @throws KafkaPublishException if publishing fails
     */
    public void publish(EventIngestionRequest request) {
        log.info("Publishing event to Kafka: cloudeventsId={}, subject={}",
                request.getId(), request.getSubject());
        inboundEventProducer.publish(request);
    }
}
