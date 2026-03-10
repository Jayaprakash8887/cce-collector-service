package org.openphc.cce.collector.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for C4 DTO layer — serialization, deserialization,
 * field naming, and bean validation. No Spring context needed.
 */
class DtoSerializationTest {

    private static ObjectMapper mapper;
    private static Validator validator;

    @BeforeAll
    static void setup() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ════════════════════════════════════════════════════════════════
    // EventIngestionRequest — Deserialization
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventIngestionRequest — Deserialization")
    class EventIngestionRequestDeserialization {

        @Test
        @DisplayName("deserializes full lowercase CloudEvents JSON")
        void fullDeserialization() throws Exception {
            String json = """
                {
                  "specversion": "1.0",
                  "id": "evt-001",
                  "source": "rhie-mediator",
                  "type": "org.openphc.cce.encounter",
                  "subject": "260115-0001-7823",
                  "time": "2026-01-15T08:30:00Z",
                  "datacontenttype": "application/fhir+json",
                  "facilityid": "0001",
                  "correlationid": "corr-abc-123",
                  "sourceeventid": "enc-001",
                  "protocolinstanceid": null,
                  "protocoldefinitionid": null,
                  "actionid": null,
                  "data": { "resourceType": "Encounter", "status": "finished" }
                }
                """;

            EventIngestionRequest req = mapper.readValue(json, EventIngestionRequest.class);

            assertThat(req.getSpecversion()).isEqualTo("1.0");
            assertThat(req.getId()).isEqualTo("evt-001");
            assertThat(req.getSource()).isEqualTo("rhie-mediator");
            assertThat(req.getType()).isEqualTo("org.openphc.cce.encounter");
            assertThat(req.getSubject()).isEqualTo("260115-0001-7823");
            assertThat(req.getTime()).isEqualTo("2026-01-15T08:30:00Z");
            assertThat(req.getDatacontenttype()).isEqualTo("application/fhir+json");
            assertThat(req.getFacilityid()).isEqualTo("0001");
            assertThat(req.getCorrelationid()).isEqualTo("corr-abc-123");
            assertThat(req.getSourceeventid()).isEqualTo("enc-001");
            assertThat(req.getData().get("resourceType").asText()).isEqualTo("Encounter");
        }

        @Test
        @DisplayName("ignores unknown fields")
        void unknownFieldsIgnored() throws Exception {
            String json = """
                {
                  "id": "evt-001",
                  "source": "test",
                  "type": "test.type",
                  "subject": "patient-1",
                  "data": {},
                  "unknownfield": "should be ignored"
                }
                """;

            EventIngestionRequest req = mapper.readValue(json, EventIngestionRequest.class);
            assertThat(req.getId()).isEqualTo("evt-001");
        }

        @Test
        @DisplayName("optional fields deserialize as null when absent")
        void optionalFieldsNull() throws Exception {
            String json = """
                {
                  "id": "evt-001",
                  "source": "test",
                  "type": "test.type",
                  "subject": "patient-1",
                  "datacontenttype": "application/fhir+json",
                  "data": {}
                }
                """;

            EventIngestionRequest req = mapper.readValue(json, EventIngestionRequest.class);
            assertThat(req.getTime()).isNull();
            assertThat(req.getCorrelationid()).isNull();
            assertThat(req.getFacilityid()).isNull();
            assertThat(req.getSourceeventid()).isNull();
        }

        @Test
        @DisplayName("lowercase field names roundtrip correctly")
        void lowercaseRoundtrip() throws Exception {
            EventIngestionRequest req = EventIngestionRequest.builder()
                    .specversion("1.0")
                    .id("evt-rt")
                    .source("test")
                    .type("test.type")
                    .subject("patient-1")
                    .datacontenttype("application/json")
                    .correlationid("corr-123")
                    .data(mapper.valueToTree(Map.of("key", "value")))
                    .build();

            String json = mapper.writeValueAsString(req);
            JsonNode node = mapper.readTree(json);

            assertThat(node.has("specversion")).isTrue();
            assertThat(node.has("datacontenttype")).isTrue();
            assertThat(node.has("correlationid")).isTrue();
            // Must NOT have camelCase variants
            assertThat(node.has("specVersion")).isFalse();
            assertThat(node.has("dataContentType")).isFalse();
            assertThat(node.has("correlationId")).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // EventIngestionRequest — Bean Validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventIngestionRequest — Bean Validation")
    class EventIngestionRequestValidation {

        private EventIngestionRequest validRequest() {
            return EventIngestionRequest.builder()
                    .specversion("1.0")
                    .id("evt-001")
                    .source("rhie-mediator")
                    .type("org.openphc.cce.encounter")
                    .subject("260115-0001-7823")
                    .datacontenttype("application/fhir+json")
                    .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                    .build();
        }

        @Test
        @DisplayName("valid request passes validation")
        void validPasses() {
            Set<ConstraintViolation<EventIngestionRequest>> violations =
                    validator.validate(validRequest());
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("missing id → violation")
        void missingId() {
            EventIngestionRequest req = validRequest();
            req.setId(null);
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getMessage().contains("id is required"));
        }

        @Test
        @DisplayName("missing source → violation")
        void missingSource() {
            EventIngestionRequest req = validRequest();
            req.setSource(null);
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getMessage().contains("source is required"));
        }

        @Test
        @DisplayName("missing type → violation")
        void missingType() {
            EventIngestionRequest req = validRequest();
            req.setType(null);
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getMessage().contains("type is required"));
        }

        @Test
        @DisplayName("missing subject → violation")
        void missingSubject() {
            EventIngestionRequest req = validRequest();
            req.setSubject(null);
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getMessage().contains("subject is required"));
        }

        @Test
        @DisplayName("missing data → violation")
        void missingData() {
            EventIngestionRequest req = validRequest();
            req.setData(null);
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getMessage().contains("data is required"));
        }

        @Test
        @DisplayName("multiple missing fields → all reported")
        void multipleMissing() {
            EventIngestionRequest req = EventIngestionRequest.builder().build();
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            Set<String> messages = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.toSet());
            assertThat(messages).contains(
                    "id is required",
                    "source is required",
                    "type is required",
                    "subject is required",
                    "data is required",
                    "datacontenttype is required"
            );
        }

        @Test
        @DisplayName("blank id → violation")
        void blankId() {
            EventIngestionRequest req = validRequest();
            req.setId("   ");
            Set<ConstraintViolation<EventIngestionRequest>> violations = validator.validate(req);
            assertThat(violations).isNotEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // EventIngestionRequest — Serialization (Kafka message format)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventIngestionRequest — Serialization (Kafka)")
    class EventIngestionRequestSerialization {

        @Test
        @DisplayName("serializes with all-lowercase field names")
        void lowercaseKeys() throws Exception {
            EventIngestionRequest req = EventIngestionRequest.builder()
                    .id("evt-001")
                    .source("rhie-mediator")
                    .type("org.openphc.cce.encounter")
                    .specversion("1.0")
                    .subject("260115-0001-7823")
                    .time("2026-01-15T08:30:00Z")
                    .datacontenttype("application/fhir+json")
                    .correlationid("corr-abc")
                    .facilityid("0001")
                    .data(mapper.valueToTree(Map.of("resourceType", "Encounter")))
                    .build();

            JsonNode node = mapper.valueToTree(req);

            // All lowercase — no camelCase
            assertThat(node.has("specversion")).isTrue();
            assertThat(node.has("datacontenttype")).isTrue();
            assertThat(node.has("correlationid")).isTrue();
            assertThat(node.has("facilityid")).isTrue();
            assertThat(node.has("sourceeventid")).isFalse(); // null → omitted

            // Must NOT have camelCase
            assertThat(node.has("specVersion")).isFalse();
            assertThat(node.has("dataContentType")).isFalse();
            assertThat(node.has("correlationId")).isFalse();
            assertThat(node.has("facilityId")).isFalse();
        }

        @Test
        @DisplayName("null fields omitted via @JsonInclude(NON_NULL)")
        void nullOmitted() throws Exception {
            EventIngestionRequest req = EventIngestionRequest.builder()
                    .id("evt-001")
                    .source("test")
                    .type("test.type")
                    .specversion("1.0")
                    .subject("patient-1")
                    .build();

            JsonNode node = mapper.valueToTree(req);

            assertThat(node.has("id")).isTrue();
            assertThat(node.has("time")).isFalse();
            assertThat(node.has("datacontenttype")).isFalse();
            assertThat(node.has("correlationid")).isFalse();
            assertThat(node.has("sourceeventid")).isFalse();
            assertThat(node.has("protocolinstanceid")).isFalse();
            assertThat(node.has("protocoldefinitionid")).isFalse();
            assertThat(node.has("actionid")).isFalse();
            assertThat(node.has("facilityid")).isFalse();
            assertThat(node.has("data")).isFalse();
        }

        @Test
        @DisplayName("time serializes as ISO-8601 string")
        void timeSerialization() throws Exception {
            EventIngestionRequest req = EventIngestionRequest.builder()
                    .id("evt-001")
                    .source("test")
                    .type("test.type")
                    .specversion("1.0")
                    .subject("patient-1")
                    .time("2026-03-01T10:00:00Z")
                    .build();

            JsonNode node = mapper.valueToTree(req);
            String timeValue = node.get("time").asText();

            // Must be ISO-8601 string, not a numeric timestamp
            assertThat(timeValue).contains("2026-03-01");
            assertThat(timeValue).contains("10:00:00");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ApiResponse — Serialization
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ApiResponse — Serialization")
    class ApiResponseSerialization {

        @Test
        @DisplayName("wraps EventIngestionResponse in 'data' envelope")
        void dataEnvelope() throws Exception {
            EventIngestionResponse inner = EventIngestionResponse.builder()
                    .eventId("uuid-123")
                    .cloudEventsId("evt-001")
                    .status("accepted")
                    .build();

            ApiResponse response = ApiResponse.of(inner);
            JsonNode node = mapper.valueToTree(response);

            assertThat(node.has("data")).isTrue();
            assertThat(node.get("data").get("eventId").asText()).isEqualTo("uuid-123");
            assertThat(node.get("data").get("status").asText()).isEqualTo("accepted");
        }

        @Test
        @DisplayName("factory method creates correct structure")
        void factoryMethod() {
            EventIngestionResponse inner = EventIngestionResponse.builder()
                    .eventId("uuid-456")
                    .cloudEventsId("evt-002")
                    .status("accepted")
                    .correlationId("corr-xyz")
                    .build();

            ApiResponse response = ApiResponse.of(inner);

            assertThat(response.data()).isNotNull();
            assertThat(response.data().getEventId()).isEqualTo("uuid-456");
            assertThat(response.data().getCloudEventsId()).isEqualTo("evt-002");
            assertThat(response.data().getStatus()).isEqualTo("accepted");
            assertThat(response.data().getCorrelationId()).isEqualTo("corr-xyz");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ApiError — Serialization
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ApiError — Serialization")
    class ApiErrorSerialization {

        @Test
        @DisplayName("wraps error in nested 'error' envelope")
        void errorEnvelope() throws Exception {
            ApiError error = ApiError.of("VALIDATION_ERROR", "Missing field: type");
            JsonNode node = mapper.valueToTree(error);

            assertThat(node.has("error")).isTrue();
            assertThat(node.get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(node.get("error").get("message").asText()).isEqualTo("Missing field: type");
        }

        @Test
        @DisplayName("factory method creates correct structure")
        void factoryMethod() {
            ApiError error = ApiError.of("INTERNAL_ERROR", "Unexpected failure");
            assertThat(error.error().code()).isEqualTo("INTERNAL_ERROR");
            assertThat(error.error().message()).isEqualTo("Unexpected failure");
        }

        @Test
        @DisplayName("all documented error codes can be represented")
        void allErrorCodes() {
            String[] codes = {
                "VALIDATION_ERROR", "FHIR_VALIDATION_ERROR", "DUPLICATE_EVENT",
                "KAFKA_PUBLISH_ERROR", "INTERNAL_ERROR", "NOT_FOUND",
                "CONSTRAINT_VIOLATION"
            };
            for (String code : codes) {
                ApiError error = ApiError.of(code, "test message");
                assertThat(error.error().code()).isEqualTo(code);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // EventIngestionResponse — Serialization
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventIngestionResponse — Serialization")
    class EventIngestionResponseSerialization {

        @Test
        @DisplayName("uses camelCase field names (CCE API format)")
        void camelCaseKeys() throws Exception {
            EventIngestionResponse resp = EventIngestionResponse.builder()
                    .eventId("uuid-123")
                    .cloudEventsId("evt-001")
                    .status("accepted")
                    .correlationId("corr-abc")
                    .receivedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();

            JsonNode node = mapper.valueToTree(resp);

            assertThat(node.has("eventId")).isTrue();
            assertThat(node.has("cloudEventsId")).isTrue();
            assertThat(node.has("correlationId")).isTrue();
            assertThat(node.has("receivedAt")).isTrue();
        }

        @Test
        @DisplayName("null fields omitted")
        void nullOmitted() throws Exception {
            EventIngestionResponse resp = EventIngestionResponse.builder()
                    .eventId("uuid-123")
                    .status("accepted")
                    .build();

            JsonNode node = mapper.valueToTree(resp);
            assertThat(node.has("eventId")).isTrue();
            assertThat(node.has("cloudEventsId")).isFalse();
            assertThat(node.has("correlationId")).isFalse();
            assertThat(node.has("receivedAt")).isFalse();
        }
    }
}
