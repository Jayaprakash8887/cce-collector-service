package org.openphc.cce.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.domain.model.InboundEvent;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventDefaultsEnricher}.
 *
 * <p>Verifies the three enrichment rules and confirms that the request
 * DTO is never mutated. Only the entity receives enriched values.
 * No Spring context needed — pure unit tests.</p>
 */
class EventDefaultsEnricherTest {

    private EventDefaultsEnricher enricher;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        enricher = new EventDefaultsEnricher();
    }

    /**
     * Builds a valid request with all optional fields absent.
     */
    private EventIngestionRequest requestWithoutOptionals() {
        return EventIngestionRequest.builder()
                .specversion("1.0")
                .id("evt-001")
                .source("rhie-mediator")
                .type("org.openphc.cce.encounter")
                .subject("UPID-12345")
                .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                .build();
    }

    /**
     * Builds a valid request with all optional fields pre-set.
     */
    private EventIngestionRequest requestWithOptionals() {
        return EventIngestionRequest.builder()
                .specversion("1.0")
                .id("evt-002")
                .source("rhie-mediator")
                .type("org.openphc.cce.encounter")
                .subject("UPID-12345")
                .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                .correlationid("existing-corr-id")
                .time("2026-03-01T10:00:00Z")
                .datacontenttype("application/json")
                .build();
    }

    /**
     * Builds a minimal InboundEvent entity with receivedAt set.
     */
    private InboundEvent baseEntity() {
        return InboundEvent.builder()
                .cloudeventsId("evt-001")
                .source("rhie-mediator")
                .type("org.openphc.cce.encounter")
                .subject("UPID-12345")
                .rawPayload("{\"resourceType\":\"Encounter\"}")
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // Correlation ID enrichment
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Correlation ID enrichment")
    class CorrelationIdEnrichment {

        @Test
        @DisplayName("generates correlationId on entity when absent in request")
        void generatesWhenAbsent() {
            EventIngestionRequest request = requestWithoutOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getCorrelationId())
                    .isNotNull()
                    .startsWith("corr-")
                    .hasSize(5 + 36); // "corr-" + UUID (36 chars)
            // Request is NOT mutated
            assertThat(request.getCorrelationid()).isNull();
        }

        @Test
        @DisplayName("preserves correlationId when present in request")
        void preservesWhenPresent() {
            EventIngestionRequest request = requestWithOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getCorrelationId()).isEqualTo("existing-corr-id");
            // Request is NOT mutated
            assertThat(request.getCorrelationid()).isEqualTo("existing-corr-id");
        }

        @Test
        @DisplayName("generates unique correlationId on each call")
        void generatesUniqueIds() {
            EventIngestionRequest req1 = requestWithoutOptionals();
            InboundEvent ent1 = baseEntity();
            EventIngestionRequest req2 = requestWithoutOptionals();
            InboundEvent ent2 = baseEntity();

            enricher.enrich(req1, ent1);
            enricher.enrich(req2, ent2);

            assertThat(ent1.getCorrelationId())
                    .isNotEqualTo(ent2.getCorrelationId());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Time enrichment
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Time enrichment")
    class TimeEnrichment {

        @Test
        @DisplayName("fills eventTime from server receivedAt when absent")
        void fillsWhenAbsent() {
            EventIngestionRequest request = requestWithoutOptionals();
            InboundEvent entity = baseEntity();
            OffsetDateTime receivedAt = entity.getReceivedAt();

            enricher.enrich(request, entity);

            assertThat(entity.getEventTime()).isEqualTo(receivedAt);
            // Request is NOT mutated
            assertThat(request.getTime()).isNull();
        }

        @Test
        @DisplayName("preserves eventTime when present in request")
        void preservesWhenPresent() {
            EventIngestionRequest request = requestWithOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getEventTime())
                    .isEqualTo(OffsetDateTime.parse("2026-03-01T10:00:00Z"));
            // Request is NOT mutated
            assertThat(request.getTime()).isEqualTo("2026-03-01T10:00:00Z");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // datacontenttype enrichment
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("datacontenttype enrichment")
    class DataContentTypeEnrichment {

        @Test
        @DisplayName("defaults dataContentType to application/fhir+json when absent")
        void defaultsWhenAbsent() {
            EventIngestionRequest request = requestWithoutOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getDataContentType()).isEqualTo("application/fhir+json");
            // Request is NOT mutated
            assertThat(request.getDatacontenttype()).isNull();
        }

        @Test
        @DisplayName("preserves dataContentType when present in request")
        void preservesWhenPresent() {
            EventIngestionRequest request = requestWithOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getDataContentType()).isEqualTo("application/json");
            // Request is NOT mutated
            assertThat(request.getDatacontenttype()).isEqualTo("application/json");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Type pass-through (NEVER modified)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Type pass-through")
    class TypePassThrough {

        @Test
        @DisplayName("type is never modified by enrichment")
        void typeNeverModified() {
            EventIngestionRequest request = requestWithoutOptionals();
            String originalType = request.getType();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(request.getType()).isEqualTo(originalType);
        }

        @Test
        @DisplayName("arbitrary type value passes through unchanged")
        void arbitraryTypePassesThrough() {
            EventIngestionRequest request = requestWithoutOptionals();
            request.setType("any.arbitrary.string");
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(request.getType()).isEqualTo("any.arbitrary.string");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Entity sync
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Entity synchronization")
    class EntitySync {

        @Test
        @DisplayName("all enriched values set on entity only")
        void allValuesSyncedToEntity() {
            EventIngestionRequest request = requestWithoutOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getCorrelationId()).isNotNull().startsWith("corr-");
            assertThat(entity.getDataContentType()).isEqualTo("application/fhir+json");
            assertThat(entity.getEventTime()).isNotNull();

            // Request remains untouched
            assertThat(request.getCorrelationid()).isNull();
            assertThat(request.getDatacontenttype()).isNull();
            assertThat(request.getTime()).isNull();
        }

        @Test
        @DisplayName("all preserved values synced to entity")
        void preservedValuesSyncedToEntity() {
            EventIngestionRequest request = requestWithOptionals();
            InboundEvent entity = baseEntity();

            enricher.enrich(request, entity);

            assertThat(entity.getCorrelationId()).isEqualTo("existing-corr-id");
            assertThat(entity.getDataContentType()).isEqualTo("application/json");
            assertThat(entity.getEventTime())
                    .isEqualTo(OffsetDateTime.parse("2026-03-01T10:00:00Z"));
        }
    }
}
