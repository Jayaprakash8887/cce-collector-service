package org.openphc.cce.collector.api.exception;

import lombok.Getter;
import org.openphc.cce.collector.domain.model.enums.RejectionReason;

import java.util.List;

/**
 * Exception thrown when FHIR or JSON payload validation fails.
 *
 * <p>Carries a list of validation errors and the applicable
 * {@link RejectionReason} so the orchestrator (C9) can persist the
 * correct rejection status on the {@code inbound_event_log} record.</p>
 */
@Getter
public class PayloadValidationException extends RuntimeException {

    private final List<String> validationErrors;
    private final RejectionReason rejectionReason;

    public PayloadValidationException(List<String> validationErrors, RejectionReason rejectionReason) {
        super("Payload validation failed: " + String.join("; ", validationErrors),
                null, false, false);
        this.validationErrors = List.copyOf(validationErrors);
        this.rejectionReason = rejectionReason;
    }
}
