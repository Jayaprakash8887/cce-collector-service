package org.openphc.cce.collector.api.exception;

import lombok.Getter;

/**
 * Thrown when synchronous Kafka publishing fails.
 *
 * <p>Mapped to HTTP 500 Internal Server Error by {@code GlobalExceptionHandler}.
 * The source system is expected to retry on this error. The event is persisted
 * in {@code inbound_event_log} with {@code status = REJECTED} and
 * {@code rejection_reason = KAFKA_PUBLISH_FAILURE}.</p>
 */
@Getter
public class KafkaPublishException extends RuntimeException {

    private final String topic;

    public KafkaPublishException(String topic, Throwable cause) {
        super("Failed to publish event to Kafka topic '" + topic + "': " + cause.getMessage(), cause);
        this.topic = topic;
    }

    public KafkaPublishException(String topic, String message) {
        super("Failed to publish event to Kafka topic '" + topic + "': " + message);
        this.topic = topic;
    }
}
