# Insights Pre-Computation Optimization

> **CCE Collector Service** — Pre-computed ingestion metrics for the Insights Service  
> **Status**: Proposed | **Target**: v1.2.0  
> **Last Updated**: 2026-05-14  
> **Deployment Model**: Fresh deployment (no existing data to migrate)

---

## 1. Problem Statement

The CCE Insights Service executes 18 queries against the `inbound_event` table (owned by the Collector Service) and 13 queries against `compliance_event_log` for event volume analytics. At production scale, these queries face two critical bottlenecks:

### 1.1 Full-Table Aggregations

Every dashboard load triggers `GROUP BY` aggregations across the entire `inbound_event` table:

| Query | Aggregation | Est. Cost at 1M rows |
|-------|------------|---------------------|
| Ingestion funnel | `GROUP BY status` | Full table scan |
| Rejection analytics | `GROUP BY rejection_reason` | Full table scan |
| Ingestion trends | `DATE_TRUNC + GROUP BY status` | Full scan + sort |
| Source data quality | `GROUP BY source, status` | Full scan + hash aggregate |
| Event count by source | `GROUP BY source` | Full scan |
| Distinct patients/facilities | `COUNT(DISTINCT subject/facility_id)` | Full scan + hash |

### 1.2 Quadratic-Cost Queries

Two queries have O(n²) worst-case complexity:

1. **Pipeline loss detection** — `NOT EXISTS` anti-join correlating `inbound_event` (ACCEPTED) with `compliance_event_log`:
   ```sql
   SELECT COUNT(*) FROM inbound_event ie
   WHERE ie.status = 'ACCEPTED'
     AND NOT EXISTS (SELECT 1 FROM compliance_event_log el
                     WHERE el.cloudeventsid = ie.cloudevents_id
                       AND el.source = ie.source)
   ```

2. **Source overlap detection** — Self-join on `inbound_event` matching same subject + resource type within a time window:
   ```sql
   SELECT ... FROM inbound_event a JOIN inbound_event b
   ON a.subject = b.subject AND a.type = b.type
   WHERE ABS(EXTRACT(EPOCH FROM (a.event_time - b.event_time))) <= :windowSeconds
   ```

---

## 2. Optimizations Owned by Collector Service

### 2.1 New Table: `ingestion_summary_daily`

**Problem:** 8 of the 18 `inbound_event` queries aggregate by `source`, `status`, and `rejection_reason`. These counts change only when new events arrive and are queried far more often than they change.

**Solution:** Maintain a pre-aggregated daily summary table, updated incrementally on each event ingestion.

**Schema:**

```sql
CREATE TABLE ingestion_summary_daily (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date     DATE NOT NULL,
    source           VARCHAR(100) NOT NULL,
    facility_id      VARCHAR(100),            -- NULL for events without facility
    status           VARCHAR(20) NOT NULL,     -- RECEIVED, ACCEPTED, REJECTED, DUPLICATE
    rejection_reason VARCHAR(50),              -- NULL unless status = REJECTED
    resource_type    VARCHAR(100),             -- extracted from raw_payload.resourceType
    event_count      BIGINT NOT NULL DEFAULT 0,
    distinct_patients BIGINT NOT NULL DEFAULT 0, -- approximate; updated via HLL or periodic recount
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (summary_date, source, facility_id, status, rejection_reason, resource_type)
);

CREATE INDEX idx_ingestion_daily_date ON ingestion_summary_daily (summary_date);
CREATE INDEX idx_ingestion_daily_source ON ingestion_summary_daily (source);
CREATE INDEX idx_ingestion_daily_status ON ingestion_summary_daily (status);
```

**Update trigger:** In `EventIngestionService`, after persisting the `InboundEvent` and determining its final status, upsert the corresponding summary row:

```sql
INSERT INTO ingestion_summary_daily (summary_date, source, facility_id, status, rejection_reason, resource_type, event_count, distinct_patients)
VALUES (:date, :source, :facilityId, :status, :rejectionReason, :resourceType, 1, 1)
ON CONFLICT (summary_date, source, facility_id, status, rejection_reason, resource_type)
DO UPDATE SET
    event_count = ingestion_summary_daily.event_count + 1,
    updated_at = now();
```

> **Note:** `distinct_patients` is approximate. Exact distinct counts require either a HyperLogLog sketch (pg extension) or periodic batch recount. For dashboard purposes, the approximate value is sufficient.

**Insights Service query replacement:**

| Current Query | Replacement |
|---------------|-------------|
| Ingestion funnel (`GROUP BY status`) | `SELECT status, SUM(event_count) FROM ingestion_summary_daily WHERE summary_date BETWEEN ? AND ? GROUP BY status` |
| Rejection analytics (`GROUP BY rejection_reason`) | `SELECT rejection_reason, SUM(event_count) FROM ingestion_summary_daily WHERE status = 'REJECTED' GROUP BY rejection_reason` |
| Ingestion trends (`DATE_TRUNC + GROUP BY`) | `SELECT summary_date, status, SUM(event_count) FROM ingestion_summary_daily GROUP BY summary_date, status` |
| Source data quality | `SELECT source, status, SUM(event_count) FROM ingestion_summary_daily GROUP BY source, status` |
| Count by source | `SELECT source, SUM(event_count) FROM ingestion_summary_daily GROUP BY source` |
| Distinct patients by source | `SELECT source, SUM(distinct_patients) FROM ingestion_summary_daily GROUP BY source` |
| Distinct facilities | `SELECT COUNT(DISTINCT facility_id) FROM ingestion_summary_daily WHERE facility_id IS NOT NULL` |

---

### 2.2 New Table: `event_volume_daily`

**Problem:** The Insights Service runs 7+ queries on `compliance_event_log` for event volume distribution (by resource type, facility, source, processing status). These are GROUP BY aggregations on millions of rows that change slowly relative to query frequency.

> **Note:** `compliance_event_log` is owned by the Compliance Service. However, the Collector Service is the natural owner of *event volume* pre-aggregation because it is the first service in the pipeline and has access to all event metadata before downstream processing.

**Solution:** The Collector Service writes a separate volume summary; the Compliance Service updates it post-processing with `processing_status`.

**Schema (owned by Collector Service):**

```sql
CREATE TABLE event_volume_daily (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date      DATE NOT NULL,
    source            VARCHAR(100) NOT NULL,
    facility_id       VARCHAR(100),
    resource_type     VARCHAR(100) NOT NULL,
    event_count       BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (summary_date, source, facility_id, resource_type)
);

CREATE INDEX idx_event_volume_daily_date ON event_volume_daily (summary_date);
CREATE INDEX idx_event_volume_daily_facility ON event_volume_daily (facility_id);
CREATE INDEX idx_event_volume_daily_source ON event_volume_daily (source);
```

**Update trigger:** In `EventIngestionService`, after ACCEPTED status, upsert:

```sql
INSERT INTO event_volume_daily (summary_date, source, facility_id, resource_type, event_count)
VALUES (:date, :source, :facilityId, :resourceType, 1)
ON CONFLICT (summary_date, source, facility_id, resource_type)
DO UPDATE SET
    event_count = event_volume_daily.event_count + 1,
    updated_at = now();
```

**Insights Service query replacement:**

| Current Query | Replacement |
|---------------|-------------|
| Event volume by resource type | `SELECT resource_type, SUM(event_count) FROM event_volume_daily WHERE summary_date BETWEEN ? AND ? GROUP BY resource_type` |
| Event volume by facility + resource type | `SELECT facility_id, resource_type, SUM(event_count) FROM event_volume_daily GROUP BY facility_id, resource_type` |
| Event trends (DATE_TRUNC) | `SELECT summary_date, resource_type, SUM(event_count) FROM event_volume_daily GROUP BY summary_date, resource_type` |
| Event volume by source | `SELECT source, SUM(event_count) FROM event_volume_daily GROUP BY source` |
| Facility event counts | `SELECT facility_id, SUM(event_count) FROM event_volume_daily GROUP BY facility_id` |

---

### 2.3 Pipeline Loss Detection — Batch Approach

**Problem:** Pipeline loss detection (`NOT EXISTS` anti-join between `inbound_event` and `compliance_event_log`) is O(n²) worst-case and queries both tables on every dashboard load.

**Solution (fresh deploy):** Include a `matched` boolean column on `inbound_event` from the initial schema. The Compliance Service sets it when it processes the event.

**Schema (included in initial `inbound_event` DDL):**

```sql
-- Within CREATE TABLE inbound_event:
    matched BOOLEAN DEFAULT false,
-- Index:
CREATE INDEX idx_inbound_event_unmatched ON inbound_event (matched) WHERE matched = false;
```

**Cross-service update:** The Compliance Service, after successfully matching an inbound event (recording it in `compliance_event_log` with `processing_status = MATCHED`), updates the corresponding `inbound_event` row:

```sql
UPDATE inbound_event SET matched = true
WHERE cloudevents_id = :cloudeventsId AND source = :source;
```

**Insights Service query replacement:**

| Current Query | Replacement |
|---------------|-------------|
| Pipeline loss count (`NOT EXISTS` anti-join, ~30 lines) | `SELECT COUNT(*) FROM inbound_event WHERE status = 'ACCEPTED' AND matched = false AND received_at BETWEEN ? AND ?` |
| Pipeline loss by source | `SELECT source, COUNT(*) FROM inbound_event WHERE status = 'ACCEPTED' AND matched = false GROUP BY source` |

> **Cross-service coordination:** This requires the Compliance Service to write to the `inbound_event` table (owned by Collector). This is acceptable because both services share the same database. The `matched` column is a simple boolean flag with no complex invariants.

---

### 2.4 Publish `inbound_event_id` in Kafka CloudEvents

**Problem:** The Compliance Service's `compliance_event_log` table duplicates 6 columns already present in `inbound_event` (`cloudevents_id`, `source`, `source_event_id`, `subject`, `type`, `event_time`). To normalize `compliance_event_log` with an `inbound_event_id` FK (see **Compliance Service §2.9**), the Compliance Service needs to know the `inbound_event.id` (UUID) at processing time. Currently, the Collector only publishes the `EventIngestionRequest` payload — the persisted entity's primary key is not included.

**Solution:** After persisting the `InboundEvent` (Step 7 in `EventIngestionService.ingest()` — status set to ACCEPTED, entity saved with UUIDv7 ID), set the entity's `id` on the `EventIngestionRequest` as a new CCE extension attribute before publishing to Kafka (Step 8).

**Code changes:**

1. **`EventIngestionRequest.java`** — Add field:
   ```java
   private String inboundeventid;  // CCE extension: inbound_event PK (UUIDv7)
   ```

2. **`EventIngestionService.ingest()`** — Between Step 7 (save) and Step 8 (publish):
   ```java
   // Step 7: Update status = ACCEPTED
   inbound.setStatus(InboundStatus.ACCEPTED);
   inbound = repository.save(inbound);

   // Step 7.5: Attach persisted ID to the request for downstream services
   request.setInboundeventid(inbound.getId().toString());

   // Step 8: Synchronous Kafka publish
   eventPublisher.publish(request);
   ```

**Downstream impact:**
- The Compliance Service's `ComplianceEngine` reads `inboundeventid` from the CloudEvent message (same pattern as `facilityid`, `correlationid`, etc.) and sets it on `EventLog.inboundEventId`
- Enables **Compliance Service §2.9** — `compliance_event_log` normalization via `inbound_event_id` FK
- The extension attribute is optional — existing events without it are handled gracefully (FK remains NULL, backfilled via `cloudeventsid + source` match)

**Kafka message change:**

```json
{
  "specversion": "1.0",
  "id": "evt-abc-123",
  "source": "openmrs/facility-1",
  "type": "org.openmrs.event.Encounter",
  "subject": "patient/upid-001",
  "facilityid": "facility-1",
  "correlationid": "corr-xyz-456",
  "inboundeventid": "019012ab-cdef-7890-abcd-ef1234567890",
  "data": { "resourceType": "Encounter", ... }
}
```

> **Backward compatibility:** The `inboundeventid` extension is additive. Existing consumers that don't read this attribute are unaffected. The Compliance Service should treat a missing `inboundeventid` as NULL (no FK set).

---

## 3. Consistency Guarantees

- **`ingestion_summary_daily`** — Updated synchronously in the same transaction as the `InboundEvent` persist. No consistency lag.
- **`event_volume_daily`** — Updated synchronously after ACCEPTED determination. Counts are always consistent with `inbound_event.status`.
- **`matched` flag** — Updated by Compliance Service asynchronously (separate transaction). There is a brief window where a newly processed event shows `matched = false`. This is acceptable for dashboard-level accuracy (the Insights Service cache TTL is 15–30 minutes).
- **`inboundeventid` extension** — Set after entity persist and before Kafka publish. The ID is guaranteed to exist in the database before the message is published. If the Kafka publish fails, the event is rejected (no orphan references).

### 3.1 No Backfill Required

Since this is a fresh deployment with no existing data, all optimizations are included in the initial schema from day 1. Summary tables populate organically as events are ingested. No backfill migrations are needed.

---

## 4. Summary of Changes

| Change | Type | Table | Updated By | Trigger |
|--------|------|-------|-----------|---------|
| New `ingestion_summary_daily` table | Table | New | Collector | Every event ingestion |
| New `event_volume_daily` table | Table | New | Collector | ACCEPTED events |
| Add `matched` to `inbound_event` | Column | Existing | Compliance Service | Event processing |
| Publish `inboundeventid` CloudEvents extension | Kafka message | — | Collector | ACCEPTED events (Step 8) |

### 4.1 Flyway Migration Plan (Fresh Deployment)

All optimizations are included in the initial schema:

| Order | Migration | Description |
|-------|-----------|-------------|
| V1 | `V1__initial_schema.sql` | `inbound_event` table with `matched` column from day 1 |
| V2 | `V2__create_ingestion_summary_daily.sql` | Pre-computed ingestion summary table |
| V3 | `V3__create_event_volume_daily.sql` | Pre-computed event volume table |
