package org.openphc.cce.collector.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Inbound DTO for CloudEvents v1.0 event ingestion.
 *
 * <p>All field names use <strong>lowercase</strong> per the CloudEvents
 * specification — no camelCase translation. Multi-word CloudEvents
 * attribute names are concatenated lowercase (e.g. {@code specversion},
 * {@code datacontenttype}, {@code correlationid}).</p>
 *
 * <p>Bean Validation annotations enforce presence of required fields.
 * Detailed business-rule validation (e.g. specversion must be "1.0",
 * id max 50 chars) is handled by {@code CloudEventValidator} (C5).</p>
 *
 * <p>Mutable class — {@code EventDefaultsEnricher} (C8) may set
 * {@code correlationid}, {@code time}, and {@code datacontenttype}
 * when absent.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventIngestionRequest {

    // ─── CloudEvents required attributes ───────────────────────────

    private String specversion;

    @NotBlank(message = "id is required")
    private String id;

    @NotBlank(message = "source is required")
    private String source;

    @NotBlank(message = "type is required")
    private String type;

    @NotBlank(message = "subject is required")
    private String subject;

    // ─── CloudEvents recommended attributes ────────────────────────

    /** ISO-8601 datetime string; filled by server if absent. */
    private String time;

    /** MIME type of data payload; defaults to {@code application/fhir+json} if absent. */
    private String datacontenttype;

    // ─── CloudEvents data ──────────────────────────────────────────

    @NotNull(message = "data is required")
    private Map<String, Object> data;

    // ─── CCE extension attributes (lowercase per CloudEvents spec) ─

    private String facilityid;
    private String correlationid;
    private String sourceeventid;
    private String protocolinstanceid;
    private String protocoldefinitionid;
    private String actionid;
}
