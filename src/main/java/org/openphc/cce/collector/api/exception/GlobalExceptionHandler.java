package org.openphc.cce.collector.api.exception;

import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.ApiError;
import org.openphc.cce.collector.api.dto.ApiResponse;
import org.openphc.cce.collector.api.dto.EventIngestionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler for the CCE Collector REST API.
 *
 * <p>Maps domain exceptions to the standard CCE response envelopes
 * ({@link ApiResponse} for success, {@link ApiError} for errors)
 * with appropriate HTTP status codes.</p>
 *
 * <h3>Exception → HTTP Status Mapping</h3>
 * <table>
 *   <tr><th>Exception</th><th>Status</th><th>Envelope</th></tr>
 *   <tr><td>{@link CloudEventValidationException}</td><td>400</td><td>ApiError</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException}</td><td>400</td><td>ApiError</td></tr>
 *   <tr><td>{@link PayloadValidationException}</td><td>422</td><td>ApiError</td></tr>
 *   <tr><td>{@link DuplicateEventException}</td><td>200</td><td>ApiResponse</td></tr>
 *   <tr><td>{@link KafkaPublishException}</td><td>500</td><td>ApiError</td></tr>
 *   <tr><td>{@link Exception}</td><td>500</td><td>ApiError</td></tr>
 * </table>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CloudEvents envelope validation failure → 400 Bad Request.
     */
    @ExceptionHandler(CloudEventValidationException.class)
    public ResponseEntity<ApiError> handleCloudEventValidation(CloudEventValidationException ex) {
        log.warn("CloudEvents validation failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_ERROR", ex.getMessage()));
    }

    /**
     * Bean Validation failure ({@code @Valid}) → 400 Bad Request.
     *
     * <p>Aggregates all field errors into a single message.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        log.warn("Bean validation failed: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_ERROR", message));
    }

    /**
     * Payload validation failure → 422 Unprocessable Entity.
     */
    @ExceptionHandler(PayloadValidationException.class)
    public ResponseEntity<ApiError> handlePayloadValidation(PayloadValidationException ex) {
        log.warn("Payload validation failed (reason={}): {}",
                ex.getRejectionReason(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("PAYLOAD_VALIDATION_ERROR", ex.getMessage()));
    }

    /**
     * Duplicate event → 200 OK (idempotent POST pattern).
     *
     * <p>Returns a success envelope ({@link ApiResponse}) instead of an
     * error envelope because the duplicate is not an error — it's the
     * expected idempotent behaviour.</p>
     */
    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ApiResponse> handleDuplicate(DuplicateEventException ex) {
        log.info("Duplicate event detected: {}", ex.getMessage());
        EventIngestionResponse response = EventIngestionResponse.builder()
                .eventId(ex.getExistingRecordId().toString())
                .status("duplicate")
                .build();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.of(response));
    }

    /**
     * Kafka publish failure → 500 Internal Server Error.
     *
     * <p>The source system is expected to retry on this error.</p>
     */
    @ExceptionHandler(KafkaPublishException.class)
    public ResponseEntity<ApiError> handleKafkaPublish(KafkaPublishException ex) {
        log.error("Event publish failed: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("EVENT_PUBLISH_FAILURE", "Event could not be published. Please retry."));
    }

    /**
     * Catch-all for unexpected errors → 500 Internal Server Error.
     *
     * <p>The error message is intentionally generic to avoid leaking
     * internal details to the caller.</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unexpected error during event processing: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
