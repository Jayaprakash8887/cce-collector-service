package org.openphc.cce.collector.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Kafka message DTO published to {@code cce.events.inbound}.
 *
 * <p>All field names use <strong>lowercase</strong> per the CloudEvents
 * specification — no field name translation is performed. HTTP lowercase
 * field names are preserved end-to-end through to Kafka.</p>
 *
 * <p>Null fields are omitted from the serialized JSON
 * ({@code @JsonInclude(NON_NULL)}).</p>
 *
 * <p>The Kafka message key is {@code subject} (patient UPID), ensuring
 * per-patient ordering within a partition.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloudEventMessage {

    // ─── CloudEvents core attributes (lowercase) ───────────────────

    private String id;
    private String source;
    private String type;
    private String specversion;
    private String subject;

    /** ISO-8601 datetime (serialized by jackson-datatype-jsr310). */
    private OffsetDateTime time;

    /** MIME type of the data payload. */
    private String datacontenttype;

    // ─── CCE extension attributes (lowercase per CloudEvents spec) ─

    private String correlationid;
    private String sourceeventid;
    private String protocolinstanceid;
    private String protocoldefinitionid;
    private String actionid;
    private String facilityid;

    // ─── Payload ───────────────────────────────────────────────────

    /** FHIR R4 resource or valid JSON object. */
    private Map<String, Object> data;
}
