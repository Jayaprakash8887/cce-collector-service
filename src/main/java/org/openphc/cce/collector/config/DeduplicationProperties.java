package org.openphc.cce.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the deduplication service.
 *
 * <p>Bound to the {@code cce.collector.dedup} prefix in
 * {@code application.yml}. The lookback window defines how far back
 * the service queries the {@code inbound_event} table when checking
 * for duplicates.</p>
 *
 * <pre>
 * cce:
 *   collector:
 *     dedup:
 *       lookback-days: 30
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "cce.collector.dedup")
@Getter
@Setter
public class DeduplicationProperties {

    /**
     * Number of days to look back when checking for duplicate events.
     * Events older than this window are not considered duplicates
     * (the DB unique constraint still serves as a permanent backstop).
     * Default: 30 days.
     */
    private int lookbackDays = 30;
}
