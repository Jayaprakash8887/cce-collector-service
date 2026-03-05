package org.openphc.cce.collector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.CloudEventValidationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Unit tests for {@link CloudEventValidator}.
 *
 * <p>Covers all 6 validation rules, edge cases, and the aggregation
 * behaviour (all errors collected, not just the first). No Spring
 * context needed — pure unit tests.</p>
 */
class CloudEventValidatorTest {

    private CloudEventValidator validator;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validator = new CloudEventValidator();
    }

    /**
     * Builds a fully valid EventIngestionRequest.
     */
    private EventIngestionRequest validRequest() {
        return EventIngestionRequest.builder()
                .specversion("1.0")
                .id("evt-001")
                .source("rhie-mediator")
                .type("org.openphc.cce.encounter")
                .subject("260115-0001-7823")
                .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    // Happy Path
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Valid CloudEvent")
    class ValidCloudEvent {

        @Test
        @DisplayName("fully valid request passes without exception")
        void validPasses() {
            assertThatCode(() -> validator.validate(validRequest()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("type = arbitrary string passes (no format restriction)")
        void arbitraryTypePasses() {
            EventIngestionRequest req = validRequest();
            req.setType("any.arbitrary.string");

            assertThatCode(() -> validator.validate(req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("type = single word passes")
        void singleWordTypePasses() {
            EventIngestionRequest req = validRequest();
            req.setType("encounter");

            assertThatCode(() -> validator.validate(req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("id at exactly 50 chars passes")
        void idAt50CharsPasses() {
            EventIngestionRequest req = validRequest();
            req.setId("a".repeat(50));

            assertThatCode(() -> validator.validate(req))
                    .doesNotThrowAnyException();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // specversion Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("specversion validation")
    class SpecversionValidation {

        @Test
        @DisplayName("null specversion → error")
        void nullSpecversion() {
            EventIngestionRequest req = validRequest();
            req.setSpecversion(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("specversion") && e.contains("required"));
        }

        @Test
        @DisplayName("blank specversion → error")
        void blankSpecversion() {
            EventIngestionRequest req = validRequest();
            req.setSpecversion("   ");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("specversion") && e.contains("required"));
        }

        @Test
        @DisplayName("specversion != '1.0' → error with actual value")
        void wrongSpecversion() {
            EventIngestionRequest req = validRequest();
            req.setSpecversion("0.3");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("specversion") && e.contains("0.3"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // id Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("id validation")
    class IdValidation {

        @Test
        @DisplayName("null id → error")
        void nullId() {
            EventIngestionRequest req = validRequest();
            req.setId(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("id") && e.contains("required"));
        }

        @Test
        @DisplayName("blank id → error")
        void blankId() {
            EventIngestionRequest req = validRequest();
            req.setId("   ");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("id") && e.contains("required"));
        }

        @Test
        @DisplayName("id exceeds 50 chars → error")
        void idTooLong() {
            EventIngestionRequest req = validRequest();
            req.setId("a".repeat(51));

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("id") && e.contains("50"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // source Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("source validation")
    class SourceValidation {

        @Test
        @DisplayName("null source → error")
        void nullSource() {
            EventIngestionRequest req = validRequest();
            req.setSource(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("source") && e.contains("required"));
        }

        @Test
        @DisplayName("blank source → error")
        void blankSource() {
            EventIngestionRequest req = validRequest();
            req.setSource("  ");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("source") && e.contains("required"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // type Validation (presence-only — no format restriction)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("type validation (presence-only)")
    class TypeValidation {

        @Test
        @DisplayName("null type → error mentioning 'type'")
        void nullType() {
            EventIngestionRequest req = validRequest();
            req.setType(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("type") && e.contains("required"));
        }

        @Test
        @DisplayName("blank type → error (INVALID_ENVELOPE, not INVALID_EVENT_TYPE)")
        void blankType() {
            EventIngestionRequest req = validRequest();
            req.setType("");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            // Error message should reference field name, not an event-type-specific code
            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("type") && e.contains("required"));
            // Must NOT contain INVALID_EVENT_TYPE
            assertThat(ex.getMessage()).doesNotContain("INVALID_EVENT_TYPE");
        }

        @Test
        @DisplayName("whitespace-only type → error")
        void whitespaceType() {
            EventIngestionRequest req = validRequest();
            req.setType("   ");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("type") && e.contains("required"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // subject Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("subject validation")
    class SubjectValidation {

        @Test
        @DisplayName("null subject → error")
        void nullSubject() {
            EventIngestionRequest req = validRequest();
            req.setSubject(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("subject") && e.contains("required"));
        }

        @Test
        @DisplayName("blank subject → error")
        void blankSubject() {
            EventIngestionRequest req = validRequest();
            req.setSubject("  ");

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("subject") && e.contains("required"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // data Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("data validation")
    class DataValidation {

        @Test
        @DisplayName("null data → error")
        void nullData() {
            EventIngestionRequest req = validRequest();
            req.setData(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("data") && e.contains("required"));
        }

        @Test
        @DisplayName("empty data map passes (non-null is sufficient)")
        void emptyDataPasses() {
            EventIngestionRequest req = validRequest();
            req.setData(mapper.valueToTree(Map.of()));

            assertThatCode(() -> validator.validate(req))
                    .doesNotThrowAnyException();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Multiple Errors Aggregation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error aggregation")
    class ErrorAggregation {

        @Test
        @DisplayName("all fields missing → all 6 errors reported")
        void allMissing() {
            EventIngestionRequest req = EventIngestionRequest.builder().build();

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            List<String> errors = ex.getValidationErrors();
            assertThat(errors).hasSize(6);
            assertThat(errors).anyMatch(e -> e.contains("specversion"));
            assertThat(errors).anyMatch(e -> e.contains("id"));
            assertThat(errors).anyMatch(e -> e.contains("source"));
            assertThat(errors).anyMatch(e -> e.contains("type"));
            assertThat(errors).anyMatch(e -> e.contains("subject"));
            assertThat(errors).anyMatch(e -> e.contains("data"));
        }

        @Test
        @DisplayName("two fields missing → exactly 2 errors")
        void twoMissing() {
            EventIngestionRequest req = validRequest();
            req.setId(null);
            req.setSubject(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors()).hasSize(2);
        }

        @Test
        @DisplayName("wrong specversion + id too long → both reported")
        void mixedErrors() {
            EventIngestionRequest req = validRequest();
            req.setSpecversion("2.0");
            req.setId("a".repeat(51));

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getValidationErrors()).hasSize(2);
            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("specversion"));
            assertThat(ex.getValidationErrors())
                    .anyMatch(e -> e.contains("id") && e.contains("50"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Exception Structure
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exception structure")
    class ExceptionStructure {

        @Test
        @DisplayName("exception message contains all error descriptions")
        void messageContainsAll() {
            EventIngestionRequest req = EventIngestionRequest.builder().build();

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThat(ex.getMessage()).contains("CloudEvents validation failed");
            assertThat(ex.getMessage()).contains("specversion");
            assertThat(ex.getMessage()).contains("id");
        }

        @Test
        @DisplayName("validation errors list is unmodifiable")
        void unmodifiableList() {
            EventIngestionRequest req = validRequest();
            req.setId(null);

            CloudEventValidationException ex =
                    catchThrowableOfType(CloudEventValidationException.class,
                            () -> validator.validate(req));

            assertThatThrownBy(() -> ex.getValidationErrors().add("hack"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
