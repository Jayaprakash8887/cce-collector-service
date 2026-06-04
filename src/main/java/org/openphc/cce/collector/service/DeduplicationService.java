package org.openphc.cce.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.config.DeduplicationProperties;
import org.openphc.cce.collector.domain.repository.InboundEventLogRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * PostgreSQL-based deduplication service with a configurable lookback window.
 *
 * <p>Checks whether an event with the same CloudEvents {@code id} and
 * {@code source} has already been ingested within the lookback window
 * (default 30 days). The database unique constraint on
 * {@code (cloudevents_id, source)} serves as a permanent backstop beyond
 * the lookback window.</p>
 *
 * <p>The Collector follows the <strong>idempotent POST</strong> pattern:
 * duplicate submissions receive HTTP 200 (not 409 Conflict) with a
 * {@code "duplicate"} status.</p>
 *
 * @see DeduplicationProperties
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final InboundEventLogRepository repository;
    private final DeduplicationProperties properties;

    /**
     * Check whether an event with the given CloudEvents id and source
     * already exists within the configurable lookback window.
     *
     * <p>Uses {@link InboundEventLogRepository#existsByCloudeventsIdAndSourceAndReceivedAtAfter}
     * which returns a lightweight boolean — no entity hydration — keeping
     * the hot path fast.</p>
     *
     * @param cloudEventsId the CloudEvents {@code id} attribute
     * @param source        the CloudEvents {@code source} attribute
     * @return {@code true} if a matching event exists within the lookback
     *         window; {@code false} otherwise
     */
    public boolean isDuplicate(String cloudEventsId, String source) {
        OffsetDateTime lookbackDate = OffsetDateTime.now()
                .minusDays(properties.getLookbackDays());

        boolean duplicate = repository.existsByCloudeventsIdAndSourceAndReceivedAtAfter(
                cloudEventsId, source, lookbackDate);

        if (duplicate) {
            log.info("Duplicate event detected: cloudEventsId='{}', source='{}'",
                    cloudEventsId, source);
        } else {
            log.debug("No duplicate found for cloudEventsId='{}', source='{}'",
                    cloudEventsId, source);
        }

        return duplicate;
    }
}
