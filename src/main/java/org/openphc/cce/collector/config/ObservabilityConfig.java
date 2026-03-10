package org.openphc.cce.collector.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;
import org.openphc.cce.collector.domain.repository.InboundEventRepository;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for the CCE Collector Service.
 *
 * <p>Registers common Micrometer tags and custom metrics. The
 * {@code application} tag is already applied globally via
 * {@code management.metrics.tags.application} in {@code application.yml}.</p>
 *
 * <h3>Custom Metrics Registered</h3>
 * <table>
 *   <tr><th>Name</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@code cce.collector.rejected.count}</td><td>Gauge</td>
 *       <td>Current count of REJECTED events in {@code inbound_event}</td></tr>
 * </table>
 *
 * <p>Counter and timer metrics are registered inline in the service classes
 * that own the instrumented operations (e.g., {@code EventIngestionService},
 * {@code InboundEventProducer}).</p>
 *
 * <p>Common tags are configured via {@code management.metrics.tags.application}
 * in {@code application.yml} — no additional {@code MeterFilter} needed.</p>
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Register a gauge that tracks the current count of rejected events in
     * the {@code inbound_event} table (status = REJECTED).
     *
     * <p>The gauge is polled lazily by Micrometer — the DB query executes only
     * when the metrics endpoint is scraped (e.g., by Prometheus).</p>
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> rejectedCountGaugeCustomizer(
            InboundEventRepository repository) {
        return registry -> Gauge.builder(
                        "cce.collector.rejected.count",
                        repository,
                        repo -> repo.countByStatus(InboundStatus.REJECTED))
                .description("Count of rejected events in inbound_event")
                .register(registry);
    }
}
