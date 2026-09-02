package org.openphc.cce.collector.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.openphc.cce.common.kafka.KafkaTopicProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic auto-creation configuration.
 *
 * <p>Declares a {@link NewTopic} bean for the inbound events topic,
 * using values from {@link KafkaPublishProperties.TopicConfig}, under the topic name
 * cce-common-util's {@link KafkaTopicProperties} supplies. Spring
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
     * @param topicProperties   the shared topic name
     * @param publishProperties this service's topic-creation settings
     * @return NewTopic bean for Spring Kafka admin to manage
     */
    @Bean
    public NewTopic inboundEventsTopic(KafkaTopicProperties topicProperties,
                                       KafkaPublishProperties publishProperties) {
        KafkaPublishProperties.TopicConfig config = publishProperties.getTopicConfig();

        log.info("Configuring Kafka topic: name={}, partitions={}, replicationFactor={}, " +
                        "retentionMs={}, cleanupPolicy={}, minInsyncReplicas={}",
                topicProperties.getInboundEvents(),
                config.getPartitions(),
                config.getReplicationFactor(),
                config.getRetentionMs(),
                config.getCleanupPolicy(),
                config.getMinInsyncReplicas());

        return TopicBuilder.name(topicProperties.getInboundEvents())
                .partitions(config.getPartitions())
                .replicas(config.getReplicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.getRetentionMs()))
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, config.getCleanupPolicy())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.getMinInsyncReplicas()))
                .build();
    }
}
