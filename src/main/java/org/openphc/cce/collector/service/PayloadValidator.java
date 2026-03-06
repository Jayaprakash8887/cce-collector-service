package org.openphc.cce.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.PayloadValidationException;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;
import org.openphc.cce.collector.fhir.FhirResourceValidator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates payload validation by branching on {@code datacontenttype}.
 *
 * <ul>
 *   <li>{@code application/fhir+json} → full FHIR R4
 *       structural validation via {@link FhirResourceValidator}</li>
 *   <li>{@code application/json} → basic JSON validity check (non-empty object)</li>
 *   <li>Any other value → reject with {@code UNSUPPORTED_CONTENT_TYPE}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PayloadValidator {

    private static final String FHIR_JSON = "application/fhir+json";
    private static final String PLAIN_JSON = "application/json";

    private final FhirResourceValidator fhirResourceValidator;

    /**
     * Validate the payload of an inbound event.
     *
     * @param request the inbound CloudEvents request
     * @return validation result containing parsed resource (for FHIR payloads)
     * @throws PayloadValidationException if validation fails
     */
    public PayloadValidationResult validatePayload(EventIngestionRequest request) {
        String contentType = request.getDatacontenttype();

        if (FHIR_JSON.equals(contentType)) {
            return validateFhir(request);
        } else if (PLAIN_JSON.equals(contentType)) {
            return validateJson(request.getData());
        } else {
            throw new PayloadValidationException(
                    List.of("Unsupported content type: " + contentType),
                    RejectionReason.UNSUPPORTED_CONTENT_TYPE);
        }
    }

    private PayloadValidationResult validateFhir(EventIngestionRequest request) {
        PayloadValidationResult result = fhirResourceValidator.validate(
                request.getData(), request.getSubject());

        if (!result.isValid()) {
            throw new PayloadValidationException(result.getErrors(), RejectionReason.INVALID_FHIR);
        }
        return result;
    }

    private PayloadValidationResult validateJson(JsonNode data) {
        if (data == null || data.isEmpty()) {
            throw new PayloadValidationException(
                    List.of("JSON payload must be a non-empty object"),
                    RejectionReason.INVALID_JSON);
        }
        return PayloadValidationResult.builder()
                .valid(true)
                .errors(List.of())
                .parsedResource(null)
                .build();
    }
}
