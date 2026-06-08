package org.openphc.cce.collector.domain.model;

import com.fasterxml.uuid.Generators;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openphc.cce.collector.domain.model.enums.InboundStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapped to the {@code inbound_event_log} table.
 *
 * <p>Single-table design: this is the only table owned by the Collector Service.
 * No FK references to other tables exist. Rejected events are tracked directly
 * on this entity via {@code status}, {@code rejectionReason}, and
 * {@code errorDetails}.</p>
 */
@Entity
@Table(name = "inbound_event_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundEventLog {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "cloudevents_id", nullable = false, length = 50)
    private String cloudeventsId;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
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

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /**
     * Generates a UUIDv7 (time-ordered) primary key before persisting.
     */
    @PrePersist
    void generateId() {
        if (this.id == null) {
            this.id = Generators.timeBasedEpochGenerator().generate();
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
