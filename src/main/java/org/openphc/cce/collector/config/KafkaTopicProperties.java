package org.openphc.cce.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for CCE Kafka topic names, publish settings,
 * and topic creation parameters.
 *
 * <p>Bound to the {@code cce.kafka} prefix in {@code application.yml}.
 * Provides the inbound topic name, synchronous publish timeout, and
 * topic creation configuration (partitions, replication factor, retention, etc.).</p>
 *
 * <pre>
 * cce:
 *   kafka:
 *     topics:
 *       inbound: cce.events.inbound
 *     publish-timeout-seconds: 30
 *     topic-config:
 *       partitions: 25
 *       replication-factor: 1
 *       retention-ms: 604800000
 *       cleanup-policy: delete
 *       min-insync-replicas: 1
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "cce.kafka")
@Getter
@Setter
public class KafkaTopicProperties {

    private Topics topics = new Topics();

    /**
     * Maximum time (in seconds) to block waiting for a Kafka broker
     * acknowledgement on a synchronous send. Default: 30 seconds.
     */
    private int publishTimeoutSeconds = 30;

    /**
     * Topic creation configuration. Used by {@code KafkaTopicConfig}
     * to programmatically create topics on application startup.
     */
    private TopicConfig topicConfig = new TopicConfig();

    @Getter
    @Setter
    public static class Topics {

        /**
         * Topic name for inbound CloudEvents events.
         * Default: {@code cce.events.inbound}.
         */
        private String inbound = "cce.events.inbound";
    }

    @Getter
    @Setter
    public static class TopicConfig {

        /**
         * Number of partitions for the inbound topic.
         * Default: 25.
         */
        private int partitions = 25;

        /**
         * Replication factor for the inbound topic.
         * Default: 1 (suitable for single-broker local dev).
         */
        private short replicationFactor = 1;

        /**
         * Retention period in milliseconds. Default: 604800000 (7 days).
         */
        private long retentionMs = 604_800_000L;

        /**
         * Cleanup policy. Default: {@code delete}.
         */
        private String cleanupPolicy = "delete";

        /**
         * Minimum number of in-sync replicas. Default: 1.
         */
        private int minInsyncReplicas = 1;
    }
}
