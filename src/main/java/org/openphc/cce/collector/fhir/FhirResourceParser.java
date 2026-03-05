package org.openphc.cce.collector.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

/**
 * Parses FHIR R4 resources from {@link JsonNode} payloads using HAPI FHIR.
 *
 * <p>The inbound {@code data} field arrives as a Jackson {@link JsonNode}. This
 * component serializes the node to JSON text and hands it to HAPI FHIR's JSON
 * parser for structural parsing.</p>
 */
@Component
@RequiredArgsConstructor
public class FhirResourceParser {

    private final FhirContext fhirContext;

    /**
     * Parse the data node into a HAPI FHIR {@link IBaseResource}.
     *
     * @param data the FHIR resource as a Jackson {@link JsonNode}
     * @return the parsed FHIR resource
     * @throws IllegalArgumentException if HAPI FHIR cannot parse the resource
     */
    public IBaseResource parse(JsonNode data) {
        try {
            String json = data.toString();
            return fhirContext.newJsonParser().parseResource(json);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Failed to parse FHIR resource: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the {@code resourceType} field from the data node.
     *
     * @param data the FHIR resource as a Jackson {@link JsonNode}
     * @return the resource type string, or {@code null} if absent or blank
     */
    public String detectResourceType(JsonNode data) {
        JsonNode resourceType = data.get("resourceType");
        if (resourceType == null || resourceType.isNull() || resourceType.asText().isBlank()) {
            return null;
        }
        return resourceType.asText();
    }
}
