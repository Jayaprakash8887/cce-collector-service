package org.openphc.cce.collector.api.exception;

/**
 * Thrown when a patient UPID cannot be extracted from a FHIR resource.
 *
 * <p>This occurs when the resource has no {@code subject} or {@code patient}
 * reference, or when the reference value is blank or null.</p>
 */
public class PatientIdNotFoundException extends RuntimeException {

    public PatientIdNotFoundException(String message) {
        super(message);
    }
}
