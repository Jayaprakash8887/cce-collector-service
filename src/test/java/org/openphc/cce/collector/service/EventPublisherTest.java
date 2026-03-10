package org.openphc.cce.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.openphc.cce.collector.kafka.InboundEventProducer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EventPublisher}.
 *
 * <p>Verifies that EventPublisher delegates to {@link InboundEventProducer}
 * and propagates exceptions. No Kafka broker or Spring context needed.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher")
class EventPublisherTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private InboundEventProducer inboundEventProducer;

    @InjectMocks
    private EventPublisher eventPublisher;

    private EventIngestionRequest buildRequest() {
        return EventIngestionRequest.builder()
                .specversion("1.0")
                .id("evt-001")
                .source("rhie-mediator")
                .type("org.openphc.cce.encounter")
                .subject("UPID-12345")
                .time("2026-03-01T10:00:00Z")
                .datacontenttype("application/fhir+json")
                .correlationid("corr-abc-123")
                .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // Delegation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Delegation to InboundEventProducer")
    class Delegation {

        @Test
        @DisplayName("delegates to InboundEventProducer with same request object")
        void delegatesToProducer() {
            EventIngestionRequest request = buildRequest();

            eventPublisher.publish(request);

            ArgumentCaptor<EventIngestionRequest> captor =
                    ArgumentCaptor.forClass(EventIngestionRequest.class);
            verify(inboundEventProducer).publish(captor.capture());
            assertThat(captor.getValue()).isSameAs(request);
        }

        @Test
        @DisplayName("passes enriched request (correlationid, time) to producer")
        void passesEnrichedRequest() {
            EventIngestionRequest request = buildRequest();
            request.setCorrelationid("corr-enriched-789");
            request.setTime("2026-03-09T14:30:00Z");

            eventPublisher.publish(request);

            ArgumentCaptor<EventIngestionRequest> captor =
                    ArgumentCaptor.forClass(EventIngestionRequest.class);
            verify(inboundEventProducer).publish(captor.capture());
            assertThat(captor.getValue().getCorrelationid()).isEqualTo("corr-enriched-789");
            assertThat(captor.getValue().getTime()).isEqualTo("2026-03-09T14:30:00Z");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Error propagation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("propagates KafkaPublishException from producer")
        void propagatesKafkaPublishException() {
            EventIngestionRequest request = buildRequest();
            doThrow(new KafkaPublishException("cce.events.inbound", "Broker unavailable"))
                    .when(inboundEventProducer).publish(any(EventIngestionRequest.class));

            assertThatThrownBy(() -> eventPublisher.publish(request))
                    .isInstanceOf(KafkaPublishException.class)
                    .hasMessageContaining("cce.events.inbound");
        }

        @Test
        @DisplayName("propagates RuntimeException from producer")
        void propagatesRuntimeException() {
            EventIngestionRequest request = buildRequest();
            doThrow(new RuntimeException("Unexpected error"))
                    .when(inboundEventProducer).publish(any(EventIngestionRequest.class));

            assertThatThrownBy(() -> eventPublisher.publish(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unexpected error");
        }
    }
}
