package org.openphc.cce.collector.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openphc.cce.collector.api.dto.ApiResponse;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.dto.EventIngestionResponse;
import org.openphc.cce.collector.service.EventIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for CloudEvents event ingestion.
 *
 * <p>Exposes {@code POST /v1/events} which accepts a CloudEvents v1.0
 * structured-mode JSON request and runs it through the 8-step
 * ingestion pipeline.</p>
 *
 * <h3>Response Status Codes</h3>
 * <ul>
 *   <li><strong>202 Accepted</strong> — Event passed all validations and
 *       was published to Kafka</li>
 *   <li><strong>200 OK</strong> — Duplicate event (idempotent);
 *       handled by {@code GlobalExceptionHandler}</li>
 *   <li><strong>400 Bad Request</strong> — Envelope validation failure;
 *       handled by {@code GlobalExceptionHandler}</li>
 *   <li><strong>422 Unprocessable Entity</strong> — Payload validation failure;
 *       handled by {@code GlobalExceptionHandler}</li>
 *   <li><strong>500 Internal Server Error</strong> — Kafka publish failure;
 *       handled by {@code GlobalExceptionHandler}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EventIngestionController {

    private final EventIngestionService eventIngestionService;

    /**
     * Ingest a single CloudEvents event.
     *
     * @param request the CloudEvents v1.0 structured-mode request body
     * @return 202 Accepted with the ingestion response on success
     */
    @PostMapping("/v1/events")
    public ResponseEntity<ApiResponse> ingestEvent(
            @Valid @RequestBody EventIngestionRequest request) {
        log.info("POST /v1/events — cloudeventsId={}, source={}",
                request.getId(), request.getSource());

        EventIngestionResponse response = eventIngestionService.ingest(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(response));
    }
}
