package org.openphc.cce.collector.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Custom Kafka health indicator that verifies broker connectivity.
 *
 * <p>Performs a lightweight {@code describeCluster()} call using the
 * Spring-managed {@link KafkaAdmin} to confirm that at least one broker
 * is reachable. This is used as a <strong>readiness</strong> signal — if
 * Kafka is unreachable, the service should stop accepting traffic because
 * it cannot publish events.</p>
 *
 * <p>The indicator is disabled when Kafka auto-configuration is excluded
 * (e.g., in test profile) via
 * {@code management.health.kafka.enabled=false}.</p>
 */
@Slf4j
@Component("kafka")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "management.health.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaHealthIndicator implements HealthIndicator {

    private static final int TIMEOUT_SECONDS = 5;

    private final KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = adminClient.describeCluster();

            String clusterId = cluster.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int nodeCount = cluster.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodeCount", nodeCount)
                    .build();
        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
