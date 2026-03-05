package org.openphc.cce.collector.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FhirResourceValidator}.
 * Uses real {@link FhirResourceParser} and {@link FhirContext} — no Spring context.
 */
class FhirResourceValidatorTest {

    private static FhirResourceValidator validator;
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void setup() {
        FhirContext ctx = FhirContext.forR4();
        FhirResourceParser parser = new FhirResourceParser(ctx);
        validator = new FhirResourceValidator(parser);
    }

    // ════════════════════════════════════════════════════════════════
    // Valid FHIR resources
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Valid FHIR resources")
    class ValidFhirResources {

        @Test
        @DisplayName("valid Encounter passes without errors")
        void validEncounter() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validEncounter(), "UPID-12345");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getParsedResource()).isInstanceOf(Encounter.class);
        }

        @Test
        @DisplayName("valid Observation passes without errors")
        void validObservation() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validObservation(), "UPID-12345");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getParsedResource()).isInstanceOf(Observation.class);
        }

        @Test
        @DisplayName("valid Condition passes without errors")
        void validCondition() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validCondition(), "UPID-12345");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getParsedResource()).isInstanceOf(Condition.class);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // resourceType validation
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resourceType validation")
    class ResourceTypeValidation {

        @Test
        @DisplayName("missing resourceType returns error")
        void missingResourceType() {
            JsonNode data = mapper.valueToTree(Map.of("id", "example", "status", "finished"));

            FhirValidationResult result = validator.validate(data, "UPID-12345");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .hasSize(1)
                    .first().asString()
                    .contains("resourceType");
            assertThat(result.getParsedResource()).isNull();
        }

        @Test
        @DisplayName("blank resourceType returns error")
        void blankResourceType() {
            JsonNode data = mapper.valueToTree(Map.of(
                    "resourceType", "   ",
                    "id", "example"));

            FhirValidationResult result = validator.validate(data, "UPID-12345");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .hasSize(1)
                    .first().asString()
                    .contains("resourceType");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Parse failure
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parse failure")
    class ParseFailure {

        @Test
        @DisplayName("unknown resourceType returns parse error")
        void unknownResourceType() {
            JsonNode data = mapper.valueToTree(Map.of(
                    "resourceType", "UnknownFooBar",
                    "id", "example"));

            FhirValidationResult result = validator.validate(data, "UPID-12345");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors())
                    .hasSize(1)
                    .first().asString()
                    .contains("Failed to parse FHIR resource");
            assertThat(result.getParsedResource()).isNull();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Subject reference check
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Subject reference check")
    class SubjectReferenceCheck {

        @Test
        @DisplayName("matching subject produces no warning")
        void matchingSubject() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validEncounter(), "UPID-12345");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).isEmpty();
        }

        @Test
        @DisplayName("mismatching subject produces warning but is still valid")
        void mismatchingSubject() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validEncounter(), "UPID-99999");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings())
                    .hasSize(1)
                    .first().asString()
                    .contains("does not match");
        }

        @Test
        @DisplayName("no subject in data produces no warning")
        void noSubjectInData() {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("resourceType", "Encounter");
            dataMap.put("id", "enc-nosub");
            dataMap.put("status", "finished");
            dataMap.put("class", Map.of(
                    "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code", "AMB"));
            JsonNode data = mapper.valueToTree(dataMap);

            FhirValidationResult result = validator.validate(data, "UPID-12345");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).isEmpty();
        }

        @Test
        @DisplayName("null subject parameter produces no warning")
        void nullSubjectParam() {
            FhirValidationResult result = validator.validate(
                    FhirResourceParserTest.validEncounter(), null);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).isEmpty();
        }
    }
}
