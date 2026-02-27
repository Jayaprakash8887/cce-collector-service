# CCE Collector Service — Data Dictionary

Consolidated reference for all database tables, columns, enums, CloudEvents fields, and Kafka message fields.

---

## 1. Database Tables

### 1.1 `inbound_event` — Request Audit Log & Rejection Tracking

Every HTTP request is persisted **as-is** before processing. Used for audit trail, primary deduplication, and rejection tracking. Rejected events are recorded directly on this table.

**Migration:** `V1__create_inbound_event.sql`

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `cloudevents_id` | `VARCHAR` | No | — | CloudEvents `id` from the source system |
| `source` | `VARCHAR` | No | — | CloudEvents `source` (e.g., `rhie-mediator`, `ebuzima/kigali-south`) |
| `type` | `VARCHAR` | No | — | CloudEvents `type` as received from emitter adaptor |
| `spec_version` | `VARCHAR` | No | `'1.0'` | CloudEvents spec version |
| `subject` | `VARCHAR` | Yes | — | Patient UPID (e.g., `260225-0002-5501`) |
| `event_time` | `TIMESTAMPTZ` | Yes | — | Source-provided event time |
| `data_content_type` | `VARCHAR` | Yes | `'application/fhir+json'` | MIME type of `data` payload |
| `facility_id` | `VARCHAR` | Yes | — | Healthcare facility FOSA ID |
| `correlation_id` | `VARCHAR` | Yes | — | Distributed tracing ID |
| `source_event_id` | `VARCHAR` | Yes | — | Source system's internal event ID |
| `raw_payload` | `JSONB` | No | — | Full original request body (immutable) |
| `status` | `VARCHAR` | No | `'RECEIVED'` | Processing status (see `InboundStatus` enum) |
| `rejection_reason` | `VARCHAR` | Yes | — | Rejection reason code (if status = `REJECTED`; see `RejectionReason` enum) |
| `failure_stage` | `VARCHAR` | Yes | — | Pipeline stage where failure occurred (see `FailureStage` enum) |
| `error_details` | `TEXT` | Yes | — | Stack trace or validation error messages |
| `resolved` | `BOOLEAN` | No | `false` | Whether a rejected event has been resolved/acknowledged |
| `resolved_at` | `TIMESTAMPTZ` | Yes | — | Resolution timestamp |
| `received_at` | `TIMESTAMPTZ` | No | `now()` | Server-side receipt timestamp (UTC) |

**Constraints:**
- `PK`: `id`
- `UNIQUE`: `(cloudevents_id, source)` — primary deduplication key

**Indexes:**
| Index | Columns | Purpose |
|-------|---------|---------|
| `uq_inbound_event_id_source` | `(cloudevents_id, source)` | Deduplication (unique constraint) |
| `idx_inbound_event_subject` | `subject` | Patient-scoped queries |
| `idx_inbound_event_source` | `source` | Source-filtered queries |
| `idx_inbound_event_status` | `status` | Status-based filtering |
| `idx_inbound_event_received` | `received_at` | Time-range queries, lookback dedup |
| `idx_inbound_event_rejection` | `rejection_reason` | Rejection reason queries (WHERE status = 'REJECTED') |
| `idx_inbound_event_unresolved` | `(status, resolved)` | Unresolved rejected event scans (WHERE status = 'REJECTED' AND resolved = false) |

---

### 1.2 `event_log` — Validated Event Outbox

Each row corresponds to exactly one Kafka message on `cce.events.inbound`. This is the outbox table — the authoritative source of truth for published events.

**Migration:** `V2__create_event_log.sql`  
**Partitioned by:** `RANGE (received_at)` — monthly partitions

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `inbound_event_id` | `UUID` | No | — | FK reference to `inbound_event.id` |
| `cloudevents_id` | `VARCHAR` | No | — | CloudEvents `id` (from source) |
| `source` | `VARCHAR` | No | — | CloudEvents `source` |
| `source_event_id` | `VARCHAR` | Yes | — | Source system's internal event ID |
| `subject` | `VARCHAR` | No | — | Patient UPID — also used as Kafka partition key |
| `type` | `VARCHAR` | No | — | **Validated** event type — must match `org.openphc.cce.<resource>` (emitter adaptor is responsible for sending the correct format) |
| `event_time` | `TIMESTAMPTZ` | No | — | Event time (original or server-generated) |
| `received_at` | `TIMESTAMPTZ` | No | — | Server receipt timestamp (UTC) |
| `correlation_id` | `VARCHAR` | No | — | Correlation ID (original or generated `corr-<uuid>`) |
| `data` | `JSONB` | No | — | FHIR R4 resource payload |
| `data_content_type` | `VARCHAR` | No | `'application/fhir+json'` | MIME type |
| `protocol_instance_id` | `UUID` | Yes | — | Pre-populated by source if known (usually null) |
| `protocol_definition_id` | `UUID` | Yes | — | Pre-populated by source if known (usually null) |
| `action_id` | `VARCHAR` | Yes | — | Pre-populated by source if known (usually null) |
| `facility_id` | `VARCHAR` | Yes | — | Healthcare facility FOSA ID |
| `publish_status` | `VARCHAR` | No | `'PENDING'` | Kafka publish state (see `PublishStatus` enum) |
| `published_at` | `TIMESTAMPTZ` | Yes | — | Timestamp of successful Kafka publish |
| `kafka_topic` | `VARCHAR` | Yes | — | Kafka topic name after publish |
| `kafka_partition` | `INT` | Yes | — | Kafka partition number after publish |
| `kafka_offset` | `BIGINT` | Yes | — | Kafka offset within partition after publish |

**Constraints:**
- `UNIQUE`: `(cloudevents_id, source, received_at)` — authoritative dedup (includes partition key)
- `UNIQUE (conditional)`: `(source, source_event_id, received_at)` WHERE `source_event_id IS NOT NULL` — secondary idempotency

**Indexes:**
| Index | Columns | Condition | Purpose |
|-------|---------|-----------|---------|
| `idx_event_log_source_sourceeventid` | `(source, source_event_id, received_at)` | `source_event_id IS NOT NULL` | Secondary dedup |
| `idx_event_log_subject` | `subject` | — | Patient-scoped queries |
| `idx_event_log_type` | `type` | — | Event type queries |
| `idx_event_log_correlation` | `correlation_id` | — | Distributed tracing |
| `idx_event_log_facility` | `facility_id` | `facility_id IS NOT NULL` | Facility-scoped queries |
| `idx_event_log_publish` | `publish_status` | `publish_status != 'PUBLISHED'` | Outbox retry scan |

**Partitions (pre-created):**
| Partition | Range |
|-----------|-------|
| `event_log_2026_01` | `2026-01-01` to `2026-02-01` |
| `event_log_2026_02` | `2026-02-01` to `2026-03-01` |
| `event_log_2026_03` | `2026-03-01` to `2026-04-01` |
| `event_log_2026_04` | `2026-04-01` to `2026-05-01` |
| `event_log_2026_05` | `2026-05-01` to `2026-06-01` |
| `event_log_2026_06` | `2026-06-01` to `2026-07-01` |

> New partitions must be added before the current range is exhausted. See `deployment-guide.md` § Adding New Partitions.

---

## 2. Enum Values

### 2.1 `InboundStatus`

Status of an `inbound_event` record as it moves through the pipeline.

| Value | Description |
|-------|-------------|
| `RECEIVED` | Initial state — event persisted, not yet processed |
| `ACCEPTED` | Validation passed, event published (or queued for publish) |
| `REJECTED` | Validation failed — see `rejection_reason` for cause |
| `DUPLICATE` | Event already seen (same `cloudevents_id` + `source`) |

### 2.2 `PublishStatus`

Kafka publish state of an `event_log` record (outbox pattern).

| Value | Description |
|-------|-------------|
| `PENDING` | Awaiting Kafka publish (may be initial or post-failure) |
| `PUBLISHED` | Successfully published to Kafka — `kafka_topic`, `kafka_partition`, `kafka_offset`, `published_at` are populated |
| `FAILED` | Publish attempted but failed — eligible for retry |

### 2.3 `RejectionReason`

Reason an event was rejected (stored on `inbound_event.rejection_reason`).

| Value | Failure Stage | Description |
|-------|---------------|-------------|
| `INVALID_ENVELOPE` | `VALIDATION` | Missing or invalid CloudEvents required fields |
| `INVALID_EVENT_TYPE` | `VALIDATION` | `type` does not match required `org.openphc.cce.<resource>` pattern |
| `INVALID_FHIR` | `VALIDATION` | FHIR R4 payload failed structural validation (only when `datacontenttype` = `application/fhir+json`) |
| `INVALID_JSON` | `VALIDATION` | Non-FHIR JSON payload is not valid JSON or is empty (when `datacontenttype` = `application/json`) |
| `UNSUPPORTED_CONTENT_TYPE` | `VALIDATION` | `datacontenttype` is not `application/fhir+json` or `application/json` |
| `DUPLICATE` | `PROCESSING` | Duplicate `(id, source)` detected within lookback window |
| `MISSING_SUBJECT` | `VALIDATION` | `subject` field missing (required by CCE for patient routing) |
| `PAYLOAD_TOO_LARGE` | `VALIDATION` | Request body exceeds `max-payload-size` (default 1 MB) |
| `DESERIALIZATION_ERROR` | `VALIDATION` | Request body could not be parsed as JSON |
| `KAFKA_PUBLISH_FAILURE` | `KAFKA_PUBLISH` | Kafka broker unavailable or publish timed out |

### 2.4 `FailureStage`

Pipeline stage where the failure occurred (stored on `inbound_event.failure_stage`).

| Value | Description |
|-------|-------------|
| `VALIDATION` | CloudEvents envelope, event type, or payload validation |
| `PROCESSING` | Deduplication or persistence |
| `KAFKA_PUBLISH` | Kafka producer send failure |

---

## 3. CloudEvents Fields (Inbound HTTP)

Inbound requests use **lowercase** field names per the CloudEvents v1.0 specification.

### 3.1 Required Fields

| Field | Type | Validation | Example |
|-------|------|------------|---------|
| `specversion` | `string` | Must be `"1.0"` | `"1.0"` |
| `id` | `string` | Non-empty, max 256 chars | `"evt-eb010001-0001-4000-8000-000000000001"` |
| `source` | `string` | Non-empty URI or short identifier | `"rhie-mediator"` |
| `type` | `string` | Non-empty, must match `org.openphc.cce.<resource>` (rejected otherwise) | `"org.openphc.cce.encounter"` |
| `subject` | `string` | Non-empty patient UPID | `"260225-0002-5501"` |
| `data` | `object` | Valid JSON; FHIR validation applied only when `datacontenttype` = `application/fhir+json` | `{ "resourceType": "Encounter", ... }` |

### 3.2 Recommended Fields

| Field | Type | Default if Absent | Example |
|-------|------|-------------------|---------|
| `time` | `string` | Server `received_at` | `"2026-02-25T08:00:00Z"` |
| `datacontenttype` | `string` | `"application/fhir+json"` | `"application/fhir+json"` or `"application/json"` |
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

## 4. Kafka Message Fields (`CloudEventMessage`)

Published to `cce.events.inbound` using **camelCase** field names matching the Compliance Service consumer contract.

| Field | Type | Nullable | Source |
|-------|------|----------|--------|
| `id` | `String` | No | `event_log.cloudevents_id` |
| `source` | `String` | No | `event_log.source` |
| `type` | `String` | No | `event_log.type` (validated) |
| `specVersion` | `String` | No | Always `"1.0"` |
| `subject` | `String` | No | `event_log.subject` — also the Kafka message key |
| `time` | `OffsetDateTime` | No | `event_log.event_time` |
| `dataContentType` | `String` | No | `event_log.data_content_type` |
| `correlationId` | `String` | No | `event_log.correlation_id` |
| `sourceEventId` | `String` | Yes | `event_log.source_event_id` |
| `protocolInstanceId` | `String` | Yes | `event_log.protocol_instance_id` (UUID → String) |
| `protocolDefinitionId` | `String` | Yes | `event_log.protocol_definition_id` (UUID → String) |
| `actionId` | `String` | Yes | `event_log.action_id` |
| `facilityId` | `String` | Yes | `event_log.facility_id` |
| `data` | `Map<String, Object>` | No | `event_log.data` (FHIR R4 resource) |

---

## 5. Field Name Mapping (HTTP → Kafka)

The Collector translates CloudEvents lowercase field names to camelCase for the Kafka message:

| HTTP Request (lowercase) | Kafka Message (camelCase) | Database Column |
|--------------------------|---------------------------|-----------------|
| `specversion` | `specVersion` | `spec_version` |
| `id` | `id` | `cloudevents_id` |
| `source` | `source` | `source` |
| `type` | `type` | `type` |
| `subject` | `subject` | `subject` |
| `time` | `time` | `event_time` |
| `datacontenttype` | `dataContentType` | `data_content_type` |
| `facilityid` | `facilityId` | `facility_id` |
| `correlationid` | `correlationId` | `correlation_id` |
| `sourceeventid` | `sourceEventId` | `source_event_id` |
| `protocolinstanceid` | `protocolInstanceId` | `protocol_instance_id` |
| `protocoldefinitionid` | `protocolDefinitionId` | `protocol_definition_id` |
| `actionid` | `actionId` | `action_id` |
| `data` | `data` | `data` / `raw_payload` |

---

## 6. Event Type Validation

The Collector **does not normalize** inbound event types — normalization is the responsibility of the emitter adaptor (openHIM mediator layer). Instead, the Collector **strictly validates** that the `type` field matches the required pattern and rejects non-conforming events.

### Required Format

```
org.openphc.cce.<resource>
```

Where `<resource>` is a lowercase FHIR R4 resource type (e.g., `encounter`, `observation`, `medicationrequest`).

### Validation Behaviour

| Inbound `type` Value | Valid? | Action |
|----------------------|--------|--------|
| `org.openphc.cce.encounter` | Yes | Accepted — passes through unchanged |
| `org.openphc.cce.observation` | Yes | Accepted — passes through unchanged |
| `cce.encounter.created` | **No** | Rejected — `INVALID_EVENT_TYPE`, 400 Bad Request |
| `cce.observation.updated` | **No** | Rejected — `INVALID_EVENT_TYPE`, 400 Bad Request |
| `custom.event.type` | **No** | Rejected — `INVALID_EVENT_TYPE`, 400 Bad Request |

> **Emitter adaptors** (openHIM mediators) are responsible for mapping source-system event types to the `org.openphc.cce.<resource>` format before submitting to the Collector.

---

## 7. FHIR Resource Types

The Collector accepts any valid FHIR R4 resource. These are the resource types commonly used in the CCE clinical workflow:

| Resource Type | Event Type | Clinical Context |
|---------------|------------|------------------|
| `Encounter` | `org.openphc.cce.encounter` | Visit registration, consultations |
| `Observation` | `org.openphc.cce.observation` | Vital signs, lab results |
| `Condition` | `org.openphc.cce.condition` | Diagnoses, chief complaints |
| `MedicationRequest` | `org.openphc.cce.medicationrequest` | Prescriptions |
| `MedicationDispense` | `org.openphc.cce.medicationdispense` | Pharmacy dispensing |
| `ServiceRequest` | `org.openphc.cce.servicerequest` | Lab orders, referrals |
| `Procedure` | `org.openphc.cce.procedure` | Clinical procedures |
| `EpisodeOfCare` | `org.openphc.cce.episodeofcare` | Care episodes |
| `DiagnosticReport` | `org.openphc.cce.diagnosticreport` | Lab and imaging reports |
| `Immunization` | `org.openphc.cce.immunization` | Vaccinations |
| `AllergyIntolerance` | `org.openphc.cce.allergyintolerance` | Allergy records |
| `CarePlan` | `org.openphc.cce.careplan` | Treatment plans |
| `Patient` | `org.openphc.cce.patient` | Patient demographics |

---

## 8. Migrations

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_inbound_event.sql` | `inbound_event` table with dedup constraint + rejection tracking columns |
| V2 | `V2__create_event_log.sql` | `event_log` table, partitioned by month (Jan–Jun 2026) |
