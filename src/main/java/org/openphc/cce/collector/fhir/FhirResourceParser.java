package org.openphc.cce.collector.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;

/**
 * Parses FHIR R4 resources from {@link JsonNode} payloads using HAPI FHIR.
 *
 * <p>The inbound {@code data} field arrives as a Jackson {@link JsonNode}. This
 * component serializes the node to JSON text and hands it to HAPI FHIR's JSON
 * parser for structural parsing.</p>
 */
@Slf4j
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
     * Detect the {@code resourceType} field and map it to the HAPI R4
     * {@link ResourceType} enum.
     *
     * @param data the FHIR resource as a Jackson {@link JsonNode}
     * @return the {@link ResourceType}, or {@code null} if the field is absent,
     *         blank, or not a recognized FHIR R4 resource type
     */
    public ResourceType detectResourceType(JsonNode data) {
        JsonNode resourceType = data.get("resourceType");
        if (resourceType == null || resourceType.isNull() || resourceType.asText().isBlank()) {
            return null;
        }
        String resourceTypeCode = resourceType.asText();
        try {
            return ResourceType.fromCode(resourceTypeCode);
        } catch (Exception e) {
            log.debug("Unrecognized FHIR resourceType '{}'", resourceTypeCode);
            return null;
        }
    }
}
