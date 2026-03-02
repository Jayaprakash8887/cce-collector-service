-- ============================================================
-- V1__create_inbound_event.sql
-- Single table: audit log, deduplication, rejection tracking
-- ============================================================

CREATE TABLE IF NOT EXISTS inbound_event (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    cloudevents_id      VARCHAR(256)    NOT NULL,
    source              VARCHAR(512)    NOT NULL,
    type                VARCHAR(512)    NOT NULL,
    spec_version        VARCHAR(10)     NOT NULL DEFAULT '1.0',
    subject             VARCHAR(512),
    event_time          TIMESTAMPTZ,
    data_content_type   VARCHAR(128)    DEFAULT 'application/fhir+json',
    facility_id         VARCHAR(128),
    correlation_id      VARCHAR(256),
    source_event_id     VARCHAR(256),
    raw_payload         JSONB           NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'RECEIVED',
    rejection_reason    VARCHAR(50),
    error_details       TEXT,
    resolved            BOOLEAN         NOT NULL DEFAULT false,
    resolved_at         TIMESTAMPTZ,
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- Primary deduplication constraint
    CONSTRAINT uq_inbound_event_id_source UNIQUE (cloudevents_id, source)
);

-- Indexes for common query patterns
CREATE INDEX idx_inbound_event_subject    ON inbound_event (subject);
CREATE INDEX idx_inbound_event_source     ON inbound_event (source);
CREATE INDEX idx_inbound_event_status     ON inbound_event (status);
CREATE INDEX idx_inbound_event_received   ON inbound_event (received_at);
CREATE INDEX idx_inbound_event_rejection  ON inbound_event (rejection_reason) WHERE status = 'REJECTED';
CREATE INDEX idx_inbound_event_unresolved ON inbound_event (status, resolved) WHERE status = 'REJECTED' AND resolved = false;
