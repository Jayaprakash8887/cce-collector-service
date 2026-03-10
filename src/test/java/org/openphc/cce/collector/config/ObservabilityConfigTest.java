package org.openphc.cce.collector.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;
import org.openphc.cce.collector.domain.repository.InboundEventRepository;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ObservabilityConfig}.
 *
 * <p>Verifies that custom metrics (gauges, common tags) are registered
 * correctly in the {@link MeterRegistry}.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ObservabilityConfig")
class ObservabilityConfigTest {

    @Mock
    private InboundEventRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private ObservabilityConfig config;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        config = new ObservabilityConfig();
    }

    // ════════════════════════════════════════════════════════════════
    // Rejected Count Gauge
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rejected Count Gauge")
    class RejectedCountGauge {

        @Test
        @DisplayName("registers cce.collector.rejected.count gauge")
        void gaugeRegistered() {
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    config.rejectedCountGaugeCustomizer(repository);
            customizer.customize(meterRegistry);

            Gauge gauge = meterRegistry.find("cce.collector.rejected.count").gauge();
            assertThat(gauge).isNotNull();
        }

        @Test
        @DisplayName("gauge returns current rejected count from repository")
        void gaugeReturnsCount() {
            when(repository.countByStatus(InboundStatus.REJECTED)).thenReturn(42L);

            MeterRegistryCustomizer<MeterRegistry> customizer =
                    config.rejectedCountGaugeCustomizer(repository);
            customizer.customize(meterRegistry);

            Gauge gauge = meterRegistry.find("cce.collector.rejected.count").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("gauge updates when repository count changes")
        void gaugeUpdates() {
            when(repository.countByStatus(InboundStatus.REJECTED))
                    .thenReturn(5L)
                    .thenReturn(10L);

            MeterRegistryCustomizer<MeterRegistry> customizer =
                    config.rejectedCountGaugeCustomizer(repository);
            customizer.customize(meterRegistry);

            Gauge gauge = meterRegistry.find("cce.collector.rejected.count").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(5.0);

            // Second scrape returns updated count
            assertThat(gauge.value()).isEqualTo(10.0);
        }
    }
}
