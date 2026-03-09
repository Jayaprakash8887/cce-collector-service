package org.openphc.cce.collector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.CloudEventMessage;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.openphc.cce.collector.domain.model.InboundEvent;
import org.springframework.stereotype.Service;

/**
 * Converts {@link InboundEvent} to {@link CloudEventMessage} and publishes
 * to Kafka.
 *
 * <p><strong>C9 stub:</strong> Conversion logic is implemented here. Actual
 * Kafka publishing via {@code InboundEventProducer} will be wired in C10.
 * Until then, {@link #publish(InboundEvent)} builds the message and logs it.</p>
 *
 * <p>All field names remain <strong>lowercase</strong> per the CloudEvents
 * specification — no field name translation is performed.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final ObjectMapper objectMapper;

    /**
     * Publish an accepted event to Kafka.
     *
     * <p>Converts the entity to a {@link CloudEventMessage} and sends it
     * to the configured inbound topic. The Kafka message key is the
     * {@code subject} (patient UPID), ensuring per-patient ordering.</p>
     *
     * <p><strong>Note:</strong> Actual Kafka send via {@code InboundEventProducer}
     * will be added in C10. This stub performs the conversion and logs.</p>
     *
     * @param event the persisted and enriched inbound event entity
     * @throws KafkaPublishException if publishing fails
     */
    public void publish(InboundEvent event) {
        CloudEventMessage message = toCloudEventMessage(event);
        log.info("Event ready for Kafka publish: cloudeventsId={}, subject={}, topic=cce.events.inbound",
                message.getId(), message.getSubject());
        // C10 will inject InboundEventProducer and call: producer.publish(message)
    }

    /**
     * Convert an {@link InboundEvent} entity to a {@link CloudEventMessage} DTO.
     *
     * <p>Package-private for testability.</p>
     *
     * @param event the entity to convert
     * @return the Kafka-ready CloudEvent message
     */
    CloudEventMessage toCloudEventMessage(InboundEvent event) {
        JsonNode data = parseRawPayload(event.getRawPayload());

        return CloudEventMessage.builder()
                .id(event.getCloudeventsId())
                .source(event.getSource())
                .type(event.getType())
                .specversion(event.getSpecVersion())
                .subject(event.getSubject())
                .time(event.getEventTime())
                .datacontenttype(event.getDataContentType())
                .correlationid(event.getCorrelationId())
                .sourceeventid(event.getSourceEventId())
                .facilityid(event.getFacilityId())
                .data(data)
                .build();
    }

    private JsonNode parseRawPayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException e) {
            throw new KafkaPublishException("cce.events.inbound",
                    "Failed to parse raw payload for Kafka message: " + e.getMessage());
        }
    }
}
