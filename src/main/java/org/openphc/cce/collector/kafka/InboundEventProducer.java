package org.openphc.cce.collector.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.openphc.cce.collector.config.KafkaPublishProperties;
import org.openphc.cce.common.kafka.KafkaTopicProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes enriched {@link EventIngestionRequest} messages to the
 * configured inbound Kafka topic.
 *
 * <p><strong>Synchronous send:</strong> blocks the calling thread until
 * the Kafka broker acknowledges the write (or the configured timeout
 * expires). This ensures the HTTP response reflects the true publish
 * outcome — no fire-and-forget, no outbox pattern.</p>
 *
 * <p><strong>Message key:</strong> {@code subject} (patient UPID),
 * ensuring per-patient ordering within a partition.</p>
 *
 * <p><strong>Idempotent producer:</strong> configured via
 * {@code enable.idempotence=true} in application.yml, preventing
 * duplicate messages on broker-side retries.</p>
 */
@Slf4j
@Component
public class InboundEventProducer {

    private final KafkaTemplate<String, EventIngestionRequest> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;
    private final KafkaPublishProperties publishProperties;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public InboundEventProducer(KafkaTemplate<String, EventIngestionRequest> kafkaTemplate,
                                KafkaTopicProperties kafkaTopicProperties,
                                KafkaPublishProperties publishProperties,
                                MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicProperties = kafkaTopicProperties;
        this.publishProperties = publishProperties;
        this.publishSuccessCounter = Counter.builder("cce.collector.kafka.publish.success")
                .description("Successful Kafka publishes")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("cce.collector.kafka.publish.failure")
                .description("Failed Kafka publishes")
                .register(meterRegistry);
    }

    /**
     * Publish an enriched event to the inbound Kafka topic.
     *
     * <p>Sends synchronously using {@link CompletableFuture#get(long, TimeUnit)}
     * with the configured timeout. The message key is {@code request.getSubject()}
     * (patient UPID).</p>
     *
     * @param request the enriched event ingestion request
     * @throws KafkaPublishException if the send fails or times out
     */
    public void publish(EventIngestionRequest request) {
        String topic = kafkaTopicProperties.getInboundEvents();
        String key = request.getSubject();
        int timeoutSeconds = publishProperties.getPublishTimeoutSeconds();

        log.debug("Publishing event to Kafka: topic={}, key={}, cloudeventsId={}",
                topic, key, request.getId());

        try {
            CompletableFuture<SendResult<String, EventIngestionRequest>> future =
                    kafkaTemplate.send(topic, key, request);

            SendResult<String, EventIngestionRequest> result =
                    future.get(timeoutSeconds, TimeUnit.SECONDS);

            log.info("Event published to Kafka: topic={}, partition={}, offset={}, key={}, cloudeventsId={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    key,
                    request.getId());
            publishSuccessCounter.increment();

        } catch (Exception ex) {
            log.error("Failed to publish event to Kafka: topic={}, key={}, cloudeventsId={}, error={}",
                    topic, key, request.getId(), ex.getMessage(), ex);
            publishFailureCounter.increment();
            throw new KafkaPublishException(topic, ex);
        }
    }
}
