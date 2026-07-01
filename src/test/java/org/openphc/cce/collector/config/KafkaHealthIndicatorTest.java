package org.openphc.cce.collector.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaHealthIndicator}.
 *
 * <p>Tests the health indicator with a mocked {@link KafkaAdmin}. The DOWN path
 * is exercised against an unreachable broker; the UP path is exercised by
 * stubbing the static {@code AdminClient.create()} factory (Mockito
 * {@code mockStatic}) so {@code describeCluster()} returns completed futures —
 * no real broker required. End-to-end behaviour against a live broker remains
 * covered by the integration tier.</p>
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

    @Nested
    @DisplayName("Health Check - UP path")
    class HealthCheckUpPath {

        @Test
        @DisplayName("returns UP with clusterId and nodeCount when broker is reachable")
        void upWhenBrokerReachable() {
            when(kafkaAdmin.getConfigurationProperties())
                    .thenReturn(Map.of("bootstrap.servers", "localhost:9092"));

            AdminClient adminClient = mock(AdminClient.class);
            DescribeClusterResult clusterResult = mock(DescribeClusterResult.class);
            when(clusterResult.clusterId()).thenReturn(KafkaFuture.completedFuture("test-cluster-id"));
            when(clusterResult.nodes())
                    .thenReturn(
                            KafkaFuture.completedFuture(
                                    List.of(new Node(0, "broker-0", 9092), new Node(1, "broker-1", 9092))));
            when(adminClient.describeCluster()).thenReturn(clusterResult);

            try (MockedStatic<AdminClient> mockedStatic = mockStatic(AdminClient.class)) {
                mockedStatic.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

                KafkaHealthIndicator indicator = new KafkaHealthIndicator(kafkaAdmin);
                Health health = indicator.health();

                assertThat(health.getStatus()).isEqualTo(Status.UP);
                assertThat(health.getDetails()).containsEntry("clusterId", "test-cluster-id");
                assertThat(health.getDetails()).containsEntry("nodeCount", 2);
            }
        }
    }
}
