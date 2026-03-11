package org.openphc.cce.collector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaHealthIndicator}.
 *
 * <p>Tests the health indicator with a mocked {@link KafkaAdmin}. Since
 * {@code AdminClient.create()} is a static factory, we can only verify
 * the DOWN path in pure unit tests (no broker available). The UP path
 * is validated via integration tests with an actual Kafka broker.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaHealthIndicator")
class KafkaHealthIndicatorTest {

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Nested
    @DisplayName("Health Check")
    class HealthCheck {

        @Test
        @DisplayName("returns DOWN when Kafka broker is unreachable")
        void downWhenBrokerUnreachable() {
            // Use an obviously-invalid bootstrap server to ensure connection failure
            when(kafkaAdmin.getConfigurationProperties())
                    .thenReturn(Map.of(
                            "bootstrap.servers", "localhost:1",
                            "connections.max.idle.ms", "500",
                            "request.timeout.ms", "500",
                            "default.api.timeout.ms", "500"
                    ));

            KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("error");
        }

        @Test
        @DisplayName("returns DOWN with error detail on connection failure")
        void errorDetailIncluded() {
            when(kafkaAdmin.getConfigurationProperties())
                    .thenReturn(Map.of(
                            "bootstrap.servers", "localhost:1",
                            "connections.max.idle.ms", "500",
                            "request.timeout.ms", "500",
                            "default.api.timeout.ms", "500"
                    ));

            KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("error")).isNotNull();
        }
    }
}
