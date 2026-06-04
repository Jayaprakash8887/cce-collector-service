# CCE Collector Service — Data Dictionary

Consolidated reference for all database tables, columns, enums, CloudEvents fields, and Kafka message fields.

---

## 1. Database Tables

### 1.1 `inbound_event_log` — Request Audit Log & Rejection Tracking

Every HTTP request is persisted **as-is** before processing. Used for audit trail, primary deduplication, and rejection tracking. Rejected events are recorded directly on this table. The full CloudEvents payload is stored in `raw_payload` (JSONB) — individual CloudEvents fields are not denormalized into separate columns.

**Migration:** `V1__create_inbound_event_log.sql`

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | — | Primary key (UUIDv7, application-generated, time-ordered) |
| `cloudevents_id` | `VARCHAR(50)` | No | — | CloudEvents `id` from the source system |
| `source` | `VARCHAR(100)` | No | — | CloudEvents `source` (e.g., `rhie-mediator`, `ebuzima/kigali-south`) |
| `correlation_id` | `VARCHAR(100)` | Yes | — | Distributed tracing ID |
| `raw_payload` | `JSONB` | No | — | Full original request body (immutable) — contains all CloudEvents fields |
| `status` | `VARCHAR(20)` | No | `'RECEIVED'` | Processing status (see `InboundStatus` enum) |
| `rejection_reason` | `VARCHAR(50)` | Yes | — | Rejection reason code (if status = `REJECTED`; see `RejectionReason` enum) |
| `error_details` | `TEXT` | Yes | — | Stack trace or validation error messages |
| `received_at` | `TIMESTAMPTZ` | No | `now()` | Server-side receipt timestamp (UTC) |

**Constraints:**
- `PK`: `id`
- `UNIQUE`: `(cloudevents_id, source)` — primary deduplication key

**Indexes:**
| Index | Columns | Purpose |
|-------|---------|---------|
| `uq_inbound_event_log_id_source` | `(cloudevents_id, source)` | Deduplication (unique constraint) |
| `idx_inbound_event_log_dedup` | `(cloudevents_id, source, received_at)` | Dedup lookback query — covers `existsBy...ReceivedAtAfter` (index-only scan) |
| `idx_inbound_event_log_source` | `source` | Source-filtered queries |
| `idx_inbound_event_log_status` | `status` | Status-based filtering and `countByStatus` queries |
| `idx_inbound_event_log_received` | `received_at` | Time-range queries |


---

## 2. Enum Values

### 2.1 `InboundStatus`

Status of an `inbound_event_log` record as it moves through the pipeline.

| Value | Description |
|-------|-------------|
| `RECEIVED` | Initial state — event persisted, not yet processed |
| `ACCEPTED` | Validation passed, event published to Kafka |
| `REJECTED` | Validation or Kafka publish failed — see `rejection_reason` for cause |
| `DUPLICATE` | Event already seen (same `cloudevents_id` + `source`) |

### 2.2 `RejectionReason`

Reason an event was rejected (stored on `inbound_event_log.rejection_reason`).

| Value | Description |
|-------|-------------|
| `INVALID_ENVELOPE` | Missing or invalid CloudEvents required fields (including missing `type`) |
| `INVALID_FHIR` | FHIR R4 payload failed structural validation (only when `datacontenttype` = `application/fhir+json`) |
| `INVALID_JSON` | Non-FHIR JSON payload is not valid JSON or is empty (when `datacontenttype` = `application/json`) |
| `UNSUPPORTED_CONTENT_TYPE` | `datacontenttype` is not `application/fhir+json` or `application/json` |
| `DUPLICATE` | Duplicate `(id, source)` detected within lookback window |
| `MISSING_SUBJECT` | `subject` field missing (required by CCE for patient routing) |
| `PAYLOAD_TOO_LARGE` | Request body exceeds `max-payload-size` (default 1 MB) |
| `DESERIALIZATION_ERROR` | Request body could not be parsed as JSON |
| `KAFKA_PUBLISH_FAILURE` | Kafka broker unavailable or publish timed out — HTTP 500 returned to caller |
| `INTERNAL_ERROR` | Unexpected failure during post-persist processing (safety net for orphaned RECEIVED records) |

---

## 3. CloudEvents Fields (Inbound HTTP)

Inbound requests use **lowercase** field names per the CloudEvents v1.0 specification.

### 3.1 Required Fields

| Field | Type | Validation | Example |
|-------|------|------------|---------|
| `specversion` | `string` | Must be `"1.0"` | `"1.0"` |
| `id` | `string` | Non-empty, max 50 chars | `"evt-eb010001-0001-4000-8000-000000000001"` |
| `source` | `string` | Non-empty URI or short identifier | `"rhie-mediator"` |
| `type` | `string` | Non-empty (mandatory CloudEvents attribute, no format restriction) | `"org.openphc.cce.encounter"` |
| `subject` | `string` | Non-empty patient UPID | `"260225-0002-5501"` |
| `data` | `object` | Valid JSON; FHIR validation applied only when `datacontenttype` = `application/fhir+json` | `{ "resourceType": "Encounter", ... }` |
| `datacontenttype` | `string` | Non-empty; must be `application/fhir+json` or `application/json` | `"application/fhir+json"` |

### 3.2 Recommended Fields

| Field | Type | Default if Absent | Example |
|-------|------|-------------------|---------|
| `time` | `string` | Server `received_at` | `"2026-02-25T08:00:00Z"` |
| `facilityid` | `string` | — | `"0002"` |
| `correlationid` | `string` | Generated `corr-<uuid>` | `"corr-1343872c-636d-506f-b041-1e571d426932"` |

### 3.3 Optional Extension Fields

| Field | Type | Description |
|-------|------|-------------|
| `sourceeventid` | `string` | Source system's internal event ID |
| `protocolinstanceid` | `string` | Pre-populated if source knows the target protocol instance |
| `protocoldefinitionid` | `string` | Pre-populated if source knows the target protocol |
| `actionid` | `string` | Pre-populated if source knows the target action/step |

---

## 4. Kafka Message Fields

Published to `cce.events.inbound` using **CloudEvents spec field names (lowercase)** — no field name translation is performed by the Collector. The Kafka message is the `EventIngestionRequest` DTO (enriched with server-side defaults), not the database entity.

| Field | Type | Nullable | Source |
|-------|------|----------|--------|
| `id` | `String` | No | Request `id` (CloudEvents id) |
| `source` | `String` | No | Request `source` |
| `type` | `String` | No | Request `type` (validated, passed through unchanged) |
| `specversion` | `String` | No | Always `"1.0"` |
| `subject` | `String` | No | Request `subject` — also the Kafka message key |
| `time` | `String` | No | Request `time` (enriched from server `receivedAt` if absent) |
| `datacontenttype` | `String` | No | Request `datacontenttype` |
| `correlationid` | `String` | No | Request `correlationid` (enriched as `corr-<uuid>` if absent) |
| `sourceeventid` | `String` | Yes | Request `sourceeventid` |
| `protocolinstanceid` | `String` | Yes | (usually null — Compliance Service resolves) |
| `protocoldefinitionid` | `String` | Yes | (usually null — Compliance Service resolves) |
| `actionid` | `String` | Yes | (usually null — Compliance Service resolves) |
| `facilityid` | `String` | Yes | Request `facilityid` |
| `data` | `JsonNode` | No | Request `data` (FHIR R4 resource or JSON object) |

---

## 5. Field Name Reference (HTTP → Database)

The Collector preserves CloudEvents lowercase field names end-to-end (HTTP → Kafka). The database stores only a minimal set of columns for deduplication and audit purposes; the full request is in `raw_payload` (JSONB).

| HTTP / Kafka Field (lowercase) | Database Column | Stored in DB? |
|-------------------------------|----------------|---------------|
| `id` | `cloudevents_id` | Yes |
| `source` | `source` | Yes |
| `correlationid` | `correlation_id` | Yes |
| `data` | `raw_payload` | Yes (full request body) |
| `specversion` | — | No (in `raw_payload`) |
| `type` | — | No (in `raw_payload`) |
| `subject` | — | No (in `raw_payload`) |
| `time` | — | No (in `raw_payload`) |
| `datacontenttype` | — | No (in `raw_payload`) |
| `facilityid` | — | No (in `raw_payload`) |
| `sourceeventid` | — | No (in `raw_payload`) |
| `protocolinstanceid` | — | No (in `raw_payload`) |
| `protocoldefinitionid` | — | No (in `raw_payload`) |
| `actionid` | — | No (in `raw_payload`) |

---

## 6. `type` Field

The `type` field is a mandatory CloudEvents v1.0 attribute. The Collector validates only that it is **present and non-empty** — it does not enforce any specific format, pattern, or whitelist. The emitter adaptor (openHIM mediator) sets the value; the Collector passes it through to Kafka unchanged.

> **Tier 1 structural matching** in the Compliance Service uses `data.resourceType` (payload), not the envelope `type`.

---

## 7. FHIR Resource Types

The Collector accepts any valid FHIR R4 resource when `datacontenttype` is `application/fhir+json`. These are the resource types commonly used in the RHIE clinical workflow:

| Resource Type | Clinical Context |
|---------------|------------------|
| `Encounter` | Visit registration, consultations, transfers |
| `Observation` | Vital signs, lab results, chief complaints |
| `Condition` | Diagnoses |
| `MedicationRequest` | Prescriptions (e-Prescription) |
| `MedicationDispense` | Pharmacy dispensing |
| `MedicationAdministration` | Medication given to patient |
| `ServiceRequest` | Lab orders, imaging orders, referrals |
| `Procedure` | Clinical procedures |
| `Immunization` | Vaccinations |
| `AllergyIntolerance` | Allergy records |
| `ImagingStudy` | Imaging results (DICOM) |
| `DiagnosticReport` | Lab and imaging reports |
| `Consent` | Patient consent records |
| `EpisodeOfCare` | Care episodes |
| `CarePlan` | Treatment plans |
| `Patient` | Patient demographics (UPID extracted from `identifier[]` matching configured system, fallback to `Patient.id`) |
| `RelatedPerson` | Related persons (UPID extracted from `patient` reference) |

---

## 8. Migrations

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_inbound_event_log.sql` | `inbound_event_log` table with dedup constraint + rejection tracking columns |
