# Insights Pre-Computation Optimization

> **CCE Collector Service** — Pre-computed ingestion metrics for the Insights Service  
> **Status**: Proposed | **Target**: v1.2.0  
> **Last Updated**: 2025-05-28  
> **Deployment Model**: Fresh deployment (no existing data to migrate)

---

## 1. Problem Statement

The CCE Insights Service executes 18 queries against the `inbound_event` table (owned by the Collector Service) and 13 queries against `compliance_event_log` for event volume analytics. At production scale, these queries face two critical bottlenecks:

### 1.1 Design Constraint: No Core Table Denormalization

> **Customer directive:** Core operational tables must NOT be modified for insights purposes (e.g., no adding `matched` column to `inbound_event`). Instead, all insights data is served from **separate pre-computed tables** that are updated incrementally in real-time by service code.
>
> These pre-computed tables are **temporary** — they will be replaced by a dedicated data pipeline in a future release.

### 1.2 Full-Table Aggregations

Every dashboard load triggers `GROUP BY` aggregations across the entire `inbound_event` table:

| Query | Aggregation | Est. Cost at 1M rows |
|-------|------------|---------------------|
| Ingestion funnel | `GROUP BY status` | Full table scan |
| Rejection analytics | `GROUP BY rejection_reason` | Full table scan |
| Ingestion trends | `DATE_TRUNC + GROUP BY status` | Full scan + sort |
| Source data quality | `GROUP BY source, status` | Full scan + hash aggregate |
| Event count by source | `GROUP BY source` | Full scan |
| Distinct patients/facilities | `COUNT(DISTINCT subject/facility_id)` | Full scan + hash |

### 1.3 Quadratic-Cost Queries

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

**Solution:** The Collector Service writes a separate volume summary for ACCEPTED events.

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

### 2.3 New Table: `pipeline_loss_daily`

**Problem:** Pipeline loss detection (`NOT EXISTS` anti-join between `inbound_event` and `compliance_event_log`) is O(n²) worst-case and queries both tables on every dashboard load. Adding a `matched` column to `inbound_event` would violate the no-denormalization constraint on core tables.

**Solution:** Maintain a separate `pipeline_loss_daily` table that the Compliance Service updates when it processes events. The delta between `event_volume_daily` (Collector) and events matched (Compliance) provides pipeline loss metrics.

**Schema:**

```sql
CREATE TABLE pipeline_loss_daily (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date     DATE NOT NULL,
    source           VARCHAR(100) NOT NULL,
    facility_id      VARCHAR(100),
    resource_type    VARCHAR(100),
    accepted_count   BIGINT NOT NULL DEFAULT 0,   -- events accepted by collector
    matched_count    BIGINT NOT NULL DEFAULT 0,   -- events matched by compliance
    unmatched_count  BIGINT GENERATED ALWAYS AS (accepted_count - matched_count) STORED,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (summary_date, source, facility_id, resource_type)
);

CREATE INDEX idx_pipeline_loss_daily_date ON pipeline_loss_daily (summary_date);
CREATE INDEX idx_pipeline_loss_daily_unmatched ON pipeline_loss_daily (unmatched_count) WHERE unmatched_count > 0;
```

**Update triggers:**

| Event | Service | Action |
|-------|---------|--------|
| Event accepted | Collector | Increment `accepted_count` (same trigger point as `event_volume_daily`) |
| Event matched | Compliance | Increment `matched_count` (cross-service write to shared DB) |

**Collector update (in `EventIngestionService`):**

```sql
INSERT INTO pipeline_loss_daily (summary_date, source, facility_id, resource_type, accepted_count)
VALUES (:date, :source, :facilityId, :resourceType, 1)
ON CONFLICT (summary_date, source, facility_id, resource_type)
DO UPDATE SET
    accepted_count = pipeline_loss_daily.accepted_count + 1,
    updated_at = now();
```

**Compliance update (in `ComplianceEngine`, after successful match):**

```sql
UPDATE pipeline_loss_daily SET
    matched_count = matched_count + 1,
    updated_at = now()
WHERE summary_date = :date AND source = :source
  AND facility_id IS NOT DISTINCT FROM :facilityId
  AND resource_type IS NOT DISTINCT FROM :resourceType;
```

**Insights Service query replacement:**

| Current Query | Replacement |
|---------------|-------------|
| Pipeline loss count (`NOT EXISTS` anti-join, ~30 lines) | `SELECT SUM(unmatched_count) FROM pipeline_loss_daily WHERE summary_date BETWEEN ? AND ?` |
| Pipeline loss by source | `SELECT source, SUM(unmatched_count) FROM pipeline_loss_daily WHERE unmatched_count > 0 GROUP BY source` |
| Pipeline loss trend | `SELECT summary_date, SUM(unmatched_count) FROM pipeline_loss_daily GROUP BY summary_date` |
| Loss rate by resource type | `SELECT resource_type, SUM(unmatched_count)::float / NULLIF(SUM(accepted_count), 0) FROM pipeline_loss_daily GROUP BY resource_type` |

> **Cross-service coordination:** Both the Collector and Compliance services write to this table (shared DB). The `matched_count` may briefly lag behind `accepted_count` during processing. For dashboard purposes (15–30 min cache TTL), this is acceptable.

---

## 3. Consistency Guarantees

- **`ingestion_summary_daily`** — Updated synchronously in the same transaction as the `InboundEvent` persist. No consistency lag.
- **`event_volume_daily`** — Updated synchronously after ACCEPTED determination. Counts are always consistent with `inbound_event.status`.
- **`pipeline_loss_daily`** — `accepted_count` updated synchronously by Collector. `matched_count` updated by Compliance in a separate transaction (brief lag acceptable for dashboard analytics).

### 3.1 No Backfill Required

Since this is a fresh deployment with no existing data, all optimizations are included in the initial schema from day 1. Summary tables populate organically as events are ingested. No backfill migrations are needed.

---

## 4. Summary of Changes

| Change | Type | Table | Updated By | Trigger |
|--------|------|-------|-----------|---------|
| New `ingestion_summary_daily` table | Table | New | Collector | Every event ingestion |
| New `event_volume_daily` table | Table | New | Collector | ACCEPTED events |
| New `pipeline_loss_daily` table | Table | New | Collector + Compliance | Event acceptance / Event matching |

> **Note:** No columns are added to the `inbound_event` table. The `inbound_event` schema remains unchanged.

### 4.1 Flyway Migration Plan (Fresh Deployment)

All optimizations are included in the initial schema:

| Order | Migration | Description |
|-------|-----------|-------------|
| V1 | `V1__initial_schema.sql` | `inbound_event` table with original schema (no insights columns) |
| V2 | `V2__create_ingestion_summary_daily.sql` | Pre-computed ingestion summary table |
| V3 | `V3__create_event_volume_daily.sql` | Pre-computed event volume table |
| V4 | `V4__create_pipeline_loss_daily.sql` | Pre-computed pipeline loss tracking table |

---

## 5. Future: Data Pipeline Replacement

All pre-computed tables defined in this document are **temporary**. They will be replaced by a dedicated data pipeline in a future release. When the data pipeline is implemented:

1. Drop the pre-computed tables (`ingestion_summary_daily`, `event_volume_daily`, `pipeline_loss_daily`)
2. Remove the corresponding repository, entity, and service code
3. Core `inbound_event` table remains completely unchanged — no rollback needed
4. The Insights Service switches from querying pre-computed tables to querying the data warehouse

