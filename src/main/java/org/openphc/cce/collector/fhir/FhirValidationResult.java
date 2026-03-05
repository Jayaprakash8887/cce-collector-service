package org.openphc.cce.collector.fhir;

import lombok.Builder;
import lombok.Getter;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

/**
 * Result of FHIR resource validation.
 *
 * <p>Contains errors (validation failures that reject the event) and warnings
 * (informational messages that do not prevent acceptance — e.g. subject
 * reference mismatch).</p>
 */
@Getter
@Builder
public class FhirValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    private final IBaseResource parsedResource;
}
