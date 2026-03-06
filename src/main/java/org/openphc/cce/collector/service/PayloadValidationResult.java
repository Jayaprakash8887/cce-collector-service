package org.openphc.cce.collector.service;

import lombok.Builder;
import lombok.Getter;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

/**
 * Result of payload validation (FHIR or JSON).
 *
 * <p>Contains errors (validation failures that reject the event) and the
 * parsed FHIR resource (if applicable).</p>
 */
@Getter
@Builder
public class PayloadValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final IBaseResource parsedResource;
}
