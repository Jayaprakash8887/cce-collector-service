package org.openphc.cce.collector.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs FHIR R4 structural validation on the {@code data} payload.
 *
 * <p>Structural validation checks that the payload can be parsed by HAPI FHIR
 * into a recognized FHIR resource. This is <em>not</em> full FHIR profile
 * validation — the Collector only verifies that the resource is structurally
 * sound.</p>
 *
 * <h3>Validation checks</h3>
 * <ol>
 *   <li>{@code data.resourceType} present and non-empty — required</li>
 *   <li>HAPI FHIR can parse the data into {@link IBaseResource} — required</li>
 *   <li>{@code data.subject.reference} matches CloudEvents {@code subject} — warning only</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FhirResourceValidator {

    private final FhirResourceParser parser;

    /**
     * Validate the FHIR payload.
     *
     * @param data    the FHIR resource as a Jackson {@link JsonNode}
     * @param subject the CloudEvents {@code subject} (patient UPID)
     * @return validation result with errors, warnings, and the parsed resource
     *         (if parsing succeeded)
     */
    public FhirValidationResult validate(JsonNode data, String subject) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check: resourceType present and non-empty
        String resourceType = parser.detectResourceType(data);
        if (resourceType == null) {
            errors.add("data.resourceType is required for FHIR resources");
            return buildResult(errors, warnings, null);
        }

        // Check: HAPI FHIR can parse the resource
        IBaseResource parsedResource;
        try {
            parsedResource = parser.parse(data);
        } catch (IllegalArgumentException e) {
            errors.add("Failed to parse FHIR resource: " + e.getMessage());
            return buildResult(errors, warnings, null);
        }

        // Check: subject reference (warning only, does not reject)
        checkSubjectReference(data, subject, warnings);

        return buildResult(errors, warnings, parsedResource);
    }

    private void checkSubjectReference(JsonNode data, String subject,
                                       List<String> warnings) {
        if (subject == null) {
            return;
        }

        JsonNode subjectNode = data.get("subject");
        if (subjectNode != null && subjectNode.isObject()) {
            JsonNode reference = subjectNode.get("reference");
            if (reference != null && !reference.isNull()) {
                String refStr = reference.asText();
                if (!refStr.contains(subject)) {
                    warnings.add("data.subject.reference '" + refStr
                            + "' does not match CloudEvents subject '" + subject + "'");
                    log.warn("Subject mismatch: data.subject.reference='{}' "
                            + "does not match CloudEvents subject='{}'", refStr, subject);
                }
            }
        }
    }

    private FhirValidationResult buildResult(List<String> errors, List<String> warnings,
                                             IBaseResource resource) {
        return FhirValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(List.copyOf(errors))
                .warnings(List.copyOf(warnings))
                .parsedResource(resource)
                .build();
    }
}
