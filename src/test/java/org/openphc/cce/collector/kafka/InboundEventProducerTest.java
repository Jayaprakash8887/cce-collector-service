package org.openphc.cce.collector.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.KafkaPublishException;
import org.openphc.cce.collector.config.KafkaTopicProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InboundEventProducer}.
 *
 * <p>Verifies synchronous Kafka publish behaviour with mocked
 * {@link KafkaTemplate}. No Kafka broker is started — all behaviour
 * is verified through Mockito interactions.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InboundEventProducer")
class InboundEventProducerTest {

    private static final String TOPIC = "cce.events.inbound";
    private static final int TIMEOUT_SECONDS = 30;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, EventIngestionRequest> kafkaTemplate;

    private KafkaTopicProperties kafkaTopicProperties;
    private InboundEventProducer producer;

    @BeforeEach
    void setUp() {
        kafkaTopicProperties = new KafkaTopicProperties();
        kafkaTopicProperties.getTopics().setInbound(TOPIC);
        kafkaTopicProperties.setPublishTimeoutSeconds(TIMEOUT_SECONDS);
        producer = new InboundEventProducer(kafkaTemplate, kafkaTopicProperties,
                new SimpleMeterRegistry());
    }

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

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, EventIngestionRequest>> successFuture() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TOPIC, 0), 0L, 0, 0L, 0, 0);
        ProducerRecord<String, EventIngestionRequest> record =
                new ProducerRecord<>(TOPIC, "UPID-12345", buildRequest());
        SendResult<String, EventIngestionRequest> result = new SendResult<>(record, metadata);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<SendResult<String, EventIngestionRequest>> failureFuture(Throwable cause) {
        CompletableFuture<SendResult<String, EventIngestionRequest>> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    // ════════════════════════════════════════════════════════════════
    // Successful publish
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful publish")
    class SuccessfulPublish {

        @Test
        @DisplayName("sends to configured topic with subject as key")
        void sendsToCorrectTopicAndKey() {
            EventIngestionRequest request = buildRequest();
            when(kafkaTemplate.send(eq(TOPIC), eq("UPID-12345"), eq(request)))
                    .thenReturn(successFuture());

            producer.publish(request);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<EventIngestionRequest> valueCaptor =
                    ArgumentCaptor.forClass(EventIngestionRequest.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
            assertThat(keyCaptor.getValue()).isEqualTo("UPID-12345");
            assertThat(valueCaptor.getValue()).isSameAs(request);
        }

        @Test
        @DisplayName("uses subject (patient UPID) as message key for ordering")
        void usesSubjectAsKey() {
            EventIngestionRequest request = buildRequest();
            request.setSubject("UPID-67890");
            when(kafkaTemplate.send(eq(TOPIC), eq("UPID-67890"), eq(request)))
                    .thenReturn(successFuture());

            producer.publish(request);

            verify(kafkaTemplate).send(TOPIC, "UPID-67890", request);
        }

        @Test
        @DisplayName("publishes enriched request directly (no conversion)")
        void publishesRequestDirectly() {
            EventIngestionRequest request = buildRequest();
            request.setCorrelationid("corr-enriched-456");
            request.setTime("2026-03-09T12:00:00Z");

            when(kafkaTemplate.send(eq(TOPIC), anyString(), any(EventIngestionRequest.class)))
                    .thenReturn(successFuture());

            producer.publish(request);

            ArgumentCaptor<EventIngestionRequest> captor =
                    ArgumentCaptor.forClass(EventIngestionRequest.class);
            verify(kafkaTemplate).send(eq(TOPIC), anyString(), captor.capture());

            EventIngestionRequest published = captor.getValue();
            assertThat(published.getCorrelationid()).isEqualTo("corr-enriched-456");
            assertThat(published.getTime()).isEqualTo("2026-03-09T12:00:00Z");
            assertThat(published.getId()).isEqualTo("evt-001");
        }

        @Test
        @DisplayName("reads topic name from KafkaTopicProperties")
        void readsTopicFromProperties() {
            kafkaTopicProperties.getTopics().setInbound("custom.topic.name");
            EventIngestionRequest request = buildRequest();
            when(kafkaTemplate.send(eq("custom.topic.name"), anyString(), any()))
                    .thenReturn(successFuture());

            producer.publish(request);

            verify(kafkaTemplate).send(eq("custom.topic.name"), anyString(), any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Failure handling
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure handling")
    class FailureHandling {

        @Test
        @DisplayName("throws KafkaPublishException on broker failure")
        void throwsOnBrokerFailure() {
            EventIngestionRequest request = buildRequest();
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failureFuture(new RuntimeException("Broker unavailable")));

            assertThatThrownBy(() -> producer.publish(request))
                    .isInstanceOf(KafkaPublishException.class)
                    .hasMessageContaining(TOPIC);
        }

        @Test
        @DisplayName("throws KafkaPublishException on timeout")
        void throwsOnTimeout() {
            EventIngestionRequest request = buildRequest();
            // Never-completing future → will time out
            CompletableFuture<SendResult<String, EventIngestionRequest>> neverComplete =
                    new CompletableFuture<>();
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(neverComplete);

            // Use a very short timeout to avoid slow tests
            kafkaTopicProperties.setPublishTimeoutSeconds(1);

            assertThatThrownBy(() -> producer.publish(request))
                    .isInstanceOf(KafkaPublishException.class)
                    .hasMessageContaining(TOPIC);
        }

        @Test
        @DisplayName("includes topic name in KafkaPublishException")
        void includesTopicInException() {
            EventIngestionRequest request = buildRequest();
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failureFuture(new RuntimeException("Connection reset")));

            assertThatThrownBy(() -> producer.publish(request))
                    .isInstanceOf(KafkaPublishException.class)
                    .satisfies(ex -> {
                        KafkaPublishException kpe = (KafkaPublishException) ex;
                        assertThat(kpe.getTopic()).isEqualTo(TOPIC);
                    });
        }

        @Test
        @DisplayName("wraps ExecutionException cause in KafkaPublishException")
        void wrapsExecutionException() {
            EventIngestionRequest request = buildRequest();
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failureFuture(
                            new ExecutionException("Send failed",
                                    new RuntimeException("Network error"))));

            assertThatThrownBy(() -> producer.publish(request))
                    .isInstanceOf(KafkaPublishException.class)
                    .hasMessageContaining(TOPIC);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Configuration
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("uses configurable publish timeout")
        void usesConfigurableTimeout() {
            kafkaTopicProperties.setPublishTimeoutSeconds(5);
            EventIngestionRequest request = buildRequest();

            CompletableFuture<SendResult<String, EventIngestionRequest>> neverComplete =
                    new CompletableFuture<>();
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(neverComplete);

            long start = System.currentTimeMillis();
            assertThatThrownBy(() -> producer.publish(request))
                    .isInstanceOf(KafkaPublishException.class);
            long elapsed = System.currentTimeMillis() - start;

            // Should have waited ~5 seconds (allow tolerance)
            assertThat(elapsed).isGreaterThanOrEqualTo(4000L);
            assertThat(elapsed).isLessThan(10000L);
        }
    }
}
