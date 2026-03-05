package org.openphc.cce.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.FhirValidationException;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;
import org.openphc.cce.collector.fhir.FhirResourceValidator;
import org.openphc.cce.collector.fhir.FhirValidationResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FhirPayloadValidator}.
 * Uses Mockito to isolate from {@link FhirResourceValidator}.
 */
@ExtendWith(MockitoExtension.class)
class FhirPayloadValidatorTest {

    @Mock
    private FhirResourceValidator fhirResourceValidator;

    private FhirPayloadValidator payloadValidator;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        payloadValidator = new FhirPayloadValidator(fhirResourceValidator);
    }

    // ════════════════════════════════════════════════════════════════
    // FHIR content type (application/fhir+json or absent)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FHIR content type")
    class FhirContentType {

        @Test
        @DisplayName("valid FHIR resource with explicit content type passes")
        void validFhirWithExplicitContentType() {
            EventIngestionRequest req = buildRequest("application/fhir+json");
            FhirValidationResult validResult = validFhirResult();

            when(fhirResourceValidator.validate(eq(req.getData()), eq(req.getSubject())))
                    .thenReturn(validResult);

            FhirValidationResult result = payloadValidator.validatePayload(req);

            assertThat(result.isValid()).isTrue();
            verify(fhirResourceValidator).validate(any(), any());
        }

        @Test
        @DisplayName("absent datacontenttype defaults to FHIR validation")
        void absentContentTypeDefaultsToFhir() {
            EventIngestionRequest req = buildRequest(null);
            FhirValidationResult validResult = validFhirResult();

            when(fhirResourceValidator.validate(eq(req.getData()), eq(req.getSubject())))
                    .thenReturn(validResult);

            FhirValidationResult result = payloadValidator.validatePayload(req);

            assertThat(result.isValid()).isTrue();
            verify(fhirResourceValidator).validate(any(), any());
        }

        @Test
        @DisplayName("invalid FHIR resource throws with INVALID_FHIR")
        void invalidFhirThrows() {
            EventIngestionRequest req = buildRequest("application/fhir+json");
            FhirValidationResult invalidResult = FhirValidationResult.builder()
                    .valid(false)
                    .errors(List.of("data.resourceType is required for FHIR resources"))
                    .warnings(List.of())
                    .parsedResource(null)
                    .build();

            when(fhirResourceValidator.validate(any(), any())).thenReturn(invalidResult);

            FhirValidationException ex = catchThrowableOfType(
                    FhirValidationException.class,
                    () -> payloadValidator.validatePayload(req));

            assertThat(ex.getRejectionReason()).isEqualTo(RejectionReason.INVALID_FHIR);
            assertThat(ex.getValidationErrors()).containsExactly(
                    "data.resourceType is required for FHIR resources");
        }

        @Test
        @DisplayName("FHIR warnings are propagated in result")
        void warningsPropagated() {
            EventIngestionRequest req = buildRequest("application/fhir+json");
            FhirValidationResult resultWithWarnings = FhirValidationResult.builder()
                    .valid(true)
                    .errors(List.of())
                    .warnings(List.of("data.subject.reference mismatch"))
                    .parsedResource(null)
                    .build();

            when(fhirResourceValidator.validate(any(), any())).thenReturn(resultWithWarnings);

            FhirValidationResult result = payloadValidator.validatePayload(req);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).containsExactly("data.subject.reference mismatch");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // JSON content type (application/json)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JSON content type")
    class JsonContentType {

        @Test
        @DisplayName("non-empty JSON payload passes")
        void nonEmptyJsonPasses() {
            EventIngestionRequest req = buildRequest("application/json");

            FhirValidationResult result = payloadValidator.validatePayload(req);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getParsedResource()).isNull();
            verify(fhirResourceValidator, never()).validate(any(), any());
        }

        @Test
        @DisplayName("empty JSON payload throws with INVALID_JSON")
        void emptyJsonThrows() {
            EventIngestionRequest req = EventIngestionRequest.builder()
                    .specversion("1.0")
                    .id("evt-001")
                    .source("test-source")
                    .type("encounter")
                    .subject("UPID-12345")
                    .datacontenttype("application/json")
                    .data(mapper.valueToTree(Map.of()))
                    .build();

            FhirValidationException ex = catchThrowableOfType(
                    FhirValidationException.class,
                    () -> payloadValidator.validatePayload(req));

            assertThat(ex.getRejectionReason()).isEqualTo(RejectionReason.INVALID_JSON);
            assertThat(ex.getValidationErrors()).containsExactly(
                    "JSON payload must be a non-empty object");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Unsupported content type
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unsupported content type")
    class UnsupportedContentType {

        @Test
        @DisplayName("text/xml is rejected")
        void textXmlRejected() {
            EventIngestionRequest req = buildRequest("text/xml");

            FhirValidationException ex = catchThrowableOfType(
                    FhirValidationException.class,
                    () -> payloadValidator.validatePayload(req));

            assertThat(ex.getRejectionReason())
                    .isEqualTo(RejectionReason.UNSUPPORTED_CONTENT_TYPE);
            assertThat(ex.getValidationErrors().get(0))
                    .contains("text/xml");
        }

        @Test
        @DisplayName("text/plain is rejected")
        void textPlainRejected() {
            EventIngestionRequest req = buildRequest("text/plain");

            FhirValidationException ex = catchThrowableOfType(
                    FhirValidationException.class,
                    () -> payloadValidator.validatePayload(req));

            assertThat(ex.getRejectionReason())
                    .isEqualTo(RejectionReason.UNSUPPORTED_CONTENT_TYPE);
            assertThat(ex.getValidationErrors().get(0))
                    .contains("text/plain");
        }

        @Test
        @DisplayName("arbitrary content type is rejected")
        void arbitraryContentTypeRejected() {
            EventIngestionRequest req = buildRequest("application/x-custom");

            FhirValidationException ex = catchThrowableOfType(
                    FhirValidationException.class,
                    () -> payloadValidator.validatePayload(req));

            assertThat(ex.getRejectionReason())
                    .isEqualTo(RejectionReason.UNSUPPORTED_CONTENT_TYPE);
        }
    }

    // ─── Helper methods ────────────────────────────────────────────

    private EventIngestionRequest buildRequest(String contentType) {
        return EventIngestionRequest.builder()
                .specversion("1.0")
                .id("evt-001")
                .source("test-source")
                .type("encounter")
                .subject("UPID-12345")
                .datacontenttype(contentType)
                .data(mapper.valueToTree(Map.of("resourceType", "Encounter", "status", "finished")))
                .build();
    }

    private FhirValidationResult validFhirResult() {
        return FhirValidationResult.builder()
                .valid(true)
                .errors(List.of())
                .warnings(List.of())
                .parsedResource(null)
                .build();
    }
}
