package org.openphc.cce.collector.service;

import org.openphc.cce.collector.api.dto.EventIngestionRequest;
import org.openphc.cce.collector.api.exception.CloudEventValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates CloudEvents v1.0 envelope attributes per the CCE specification.
 *
 * <p>All seven validation rules are evaluated on every call — the validator
 * collects <em>all</em> violations before throwing, so callers receive a
 * complete error list in a single pass.</p>
 *
 * <h3>Validation Rules</h3>
 * <ol>
 *   <li>{@code specversion} — must be exactly {@code "1.0"}</li>
 *   <li>{@code id} — required, non-blank, max 50 characters</li>
 *   <li>{@code source} — required, non-blank</li>
 *   <li>{@code type} — required, non-blank (presence-only; no format restriction)</li>
 *   <li>{@code subject} — required, non-blank (CCE patient UPID)</li>
 *   <li>{@code data} — required, non-null</li>
 *   <li>{@code datacontenttype} — required, non-blank</li>
 * </ol>
 *
 * <p>The {@code type} field is validated <strong>only for presence</strong>.
 * The Collector does not enforce any regex, pattern, or whitelist on the
 * type value.  Emitter adaptors (openHIM mediators) set the type; the
 * Collector passes it through unchanged.</p>
 *
 * @see CloudEventValidationException
 */
@Service
public class CloudEventValidator {

    private static final String SUPPORTED_SPEC_VERSION = "1.0";
    private static final int MAX_ID_LENGTH = 50;

    /**
     * Validates the given request against CloudEvents v1.0 rules.
     *
     * @param request the inbound event request to validate
     * @throws CloudEventValidationException if one or more rules are violated
     */
    public void validate(EventIngestionRequest request) {
        List<String> errors = new ArrayList<>();

        // Rule a: specversion must be "1.0"
        if (isBlank(request.getSpecversion())) {
            errors.add("specversion is required");
        } else if (!SUPPORTED_SPEC_VERSION.equals(request.getSpecversion())) {
            errors.add("specversion must be '1.0', got '" + request.getSpecversion() + "'");
        }

        // Rule b: id — required, non-blank, max 50 chars
        if (isBlank(request.getId())) {
            errors.add("id is required");
        } else if (request.getId().length() > MAX_ID_LENGTH) {
            errors.add("id must not exceed " + MAX_ID_LENGTH + " characters");
        }

        // Rule c: source — required, non-blank
        if (isBlank(request.getSource())) {
            errors.add("source is required");
        }

        // Rule d: type — required, non-blank (no format restriction)
        if (isBlank(request.getType())) {
            errors.add("type is required");
        }

        // Rule e: subject — required, non-blank
        if (isBlank(request.getSubject())) {
            errors.add("subject is required");
        }

        // Rule f: data — required, non-null
        if (request.getData() == null) {
            errors.add("data is required");
        }

        // Rule g: datacontenttype — required, non-blank
        if (isBlank(request.getDatacontenttype())) {
            errors.add("datacontenttype is required");
        }

        if (!errors.isEmpty()) {
            throw new CloudEventValidationException(errors);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
