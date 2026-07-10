-- ============================================================
-- V2__add_event_time_to_inbound_event_log.sql
-- Adds event_time: the clinical occurrence time of the event
-- (when the clinical act actually happened), extracted from the
-- FHIR payload. Best-effort; falls back to the CloudEvents
-- envelope time when a clinical field is absent or unparseable.
-- ============================================================

ALTER TABLE inbound_event_log
    ADD COLUMN IF NOT EXISTS event_time TIMESTAMPTZ;

-- Supports querying/ordering events by real-world clinical time
-- rather than ingestion time (received_at).
CREATE INDEX IF NOT EXISTS idx_inbound_event_log_event_time
    ON inbound_event_log (event_time);
