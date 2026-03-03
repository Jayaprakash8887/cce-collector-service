package org.openphc.cce.collector.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code inbound_event} table.
 *
 * <p>Single-table design: this is the only table owned by the Collector Service.
 * No FK references to other tables exist. Rejected events are tracked directly
 * on this entity via {@code status}, {@code rejectionReason}, and
 * {@code errorDetails}.</p>
 */
@Entity
@Table(name = "inbound_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cloudevents_id", nullable = false, length = 50)
    private String cloudeventsId;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "spec_version", nullable = false, length = 10)
    @Builder.Default
    private String specVersion = "1.0";

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @Column(name = "data_content_type", length = 50)
    @Builder.Default
    private String dataContentType = "application/fhir+json";

    @Column(name = "facility_id", length = 100)
    private String facilityId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "source_event_id", length = 100)
    private String sourceEventId;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InboundStatus status = InboundStatus.RECEIVED;

    /**
     * Stored as VARCHAR (not enum column) to allow flexibility for future
     * reason codes without requiring a schema migration.
     */
    @Column(name = "rejection_reason", length = 50)
    private String rejectionReason;

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;

    @Column(name = "received_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    /**
     * Generates a UUIDv7 (time-ordered) primary key before persisting.
     */
    @PrePersist
    void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }
}
