package org.openphc.cce.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for CCE Kafka topic names and publish settings.
 *
 * <p>Bound to the {@code cce.kafka} prefix in {@code application.yml}.
 * Provides the inbound topic name and synchronous publish timeout.</p>
 *
 * <pre>
 * cce:
 *   kafka:
 *     topics:
 *       inbound: cce.events.inbound
 *     publish-timeout-seconds: 30
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

    @Getter
    @Setter
    public static class Topics {

        /**
         * Topic name for inbound CloudEvents events.
         * Default: {@code cce.events.inbound}.
         */
        private String inbound = "cce.events.inbound";
    }
}
