package org.openphc.cce.collector.api.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.collector.api.dto.ApiError;
import org.openphc.cce.collector.api.dto.ApiResponse;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Tests each exception handler in isolation (no Spring context).
 * Verifies HTTP status codes and response envelope structure.</p>
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("CloudEventValidationException → 400")
    class CloudEventValidation {

        @Test
        @DisplayName("Returns 400 with VALIDATION_ERROR code")
        void returns400WithValidationError() {
            var ex = new CloudEventValidationException(
                    List.of("id is required", "source is required"));

            ResponseEntity<ApiError> response = handler.handleCloudEventValidation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().error().message()).contains("id is required");
            assertThat(response.getBody().error().message()).contains("source is required");
        }
    }

    @Nested
    @DisplayName("PayloadValidationException → 422")
    class FhirValidation {

        @Test
        @DisplayName("Returns 422 with PAYLOAD_VALIDATION_ERROR code")
        void returns422WithPayloadValidationError() {
            var ex = new PayloadValidationException(
                    List.of("Missing resourceType"), RejectionReason.INVALID_FHIR);

            ResponseEntity<ApiError> response = handler.handlePayloadValidation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo("PAYLOAD_VALIDATION_ERROR");
            assertThat(response.getBody().error().message()).contains("Missing resourceType");
        }

        @Test
        @DisplayName("Returns 422 for unsupported content type")
        void returns422ForUnsupportedContentType() {
            var ex = new PayloadValidationException(
                    List.of("Unsupported content type: text/xml"),
                    RejectionReason.UNSUPPORTED_CONTENT_TYPE);

            ResponseEntity<ApiError> response = handler.handlePayloadValidation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().error().message())
                    .contains("Unsupported content type");
        }
    }

    @Nested
    @DisplayName("DuplicateEventException → 200")
    class DuplicateEvent {

        @Test
        @DisplayName("Returns 200 with duplicate status in ApiResponse envelope")
        void returns200WithDuplicateStatus() {
            UUID existingId = UUID.randomUUID();
            var ex = new DuplicateEventException(existingId, existingId);

            ResponseEntity<ApiResponse> response = handler.handleDuplicate(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().getEventId()).isEqualTo(existingId.toString());
            assertThat(response.getBody().data().getStatus()).isEqualTo("duplicate");
        }

        @Test
        @DisplayName("Response uses ApiResponse (success envelope), not ApiError")
        void usesSuccessEnvelope() {
            UUID existingId = UUID.randomUUID();
            var ex = new DuplicateEventException(existingId, existingId);

            ResponseEntity<ApiResponse> response = handler.handleDuplicate(ex);

            // Verify it's ApiResponse type (success), not ApiError
            assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        }
    }

    @Nested
    @DisplayName("KafkaPublishException → 500")
    class KafkaPublishFailure {

        @Test
        @DisplayName("Returns 500 with EVENT_PUBLISH_FAILURE code and generic message")
        void returns500WithEventPublishFailure() {
            var ex = new KafkaPublishException("cce.events.inbound", "Broker unavailable");

            ResponseEntity<ApiError> response = handler.handleKafkaPublish(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo("EVENT_PUBLISH_FAILURE");
            assertThat(response.getBody().error().message())
                    .isEqualTo("Event could not be published. Please retry.");
            // Must NOT leak implementation details
            assertThat(response.getBody().error().message()).doesNotContain("Kafka");
            assertThat(response.getBody().error().message()).doesNotContain("Broker");
            assertThat(response.getBody().error().message()).doesNotContain("cce.events");
        }
    }

    @Nested
    @DisplayName("Unexpected Exception → 500")
    class UnexpectedException {

        @Test
        @DisplayName("Returns 500 with generic INTERNAL_ERROR message")
        void returns500WithGenericMessage() {
            var ex = new RuntimeException("Something unexpected broke");

            ResponseEntity<ApiError> response = handler.handleGeneral(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
            // Generic message — does NOT leak internal details
            assertThat(response.getBody().error().message())
                    .isEqualTo("An unexpected error occurred");
            assertThat(response.getBody().error().message())
                    .doesNotContain("Something unexpected broke");
        }
    }
}
