package org.openphc.cce.collector.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic auto-creation configuration.
 *
 * <p>Declares a {@link NewTopic} bean for the inbound events topic,
 * using values from {@link KafkaTopicProperties.TopicConfig}. Spring
 * Kafka's {@code KafkaAdmin} will create the topic on startup if it
 * does not already exist.</p>
 *
 * <p><strong>Note:</strong> If the topic already exists, the broker
 * will not alter partitions or replication factor. To change partitions
 * on an existing topic, use {@code kafka-topics.sh --alter}.</p>
 */
@Slf4j
@Configuration
public class KafkaTopicConfig {

    /**
     * Creates the inbound events topic with configured partitions,
     * replication factor, retention, and cleanup policy.
     *
     * @param properties Kafka topic properties from application.yml
     * @return NewTopic bean for Spring Kafka admin to manage
     */
    @Bean
    public NewTopic inboundEventsTopic(KafkaTopicProperties properties) {
        KafkaTopicProperties.TopicConfig config = properties.getTopicConfig();

        log.info("Configuring Kafka topic: name={}, partitions={}, replicationFactor={}, " +
                        "retentionMs={}, cleanupPolicy={}, minInsyncReplicas={}",
                properties.getTopics().getInbound(),
                config.getPartitions(),
                config.getReplicationFactor(),
                config.getRetentionMs(),
                config.getCleanupPolicy(),
                config.getMinInsyncReplicas());

        return TopicBuilder.name(properties.getTopics().getInbound())
                .partitions(config.getPartitions())
                .replicas(config.getReplicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.getRetentionMs()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, config.getCleanupPolicy())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.getMinInsyncReplicas()))
                .build();
    }
}
