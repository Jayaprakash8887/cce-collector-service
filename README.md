# CCE Collector Service

> **Release 1.0.0**

The **Collector Service** is the event ingestion gateway of the Care Coordination Engine (CCE). It receives clinical events from external EHR/RHIE systems, validates and normalizes them into CloudEvents v1.0 envelopes with FHIR R4 payloads, and publishes them to Kafka for downstream processing.

## Technology Stack

| Concern | Technology | Version |
|---------|------------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.4.x |
| Build tool | Maven | 3.9+ |
| Database | PostgreSQL | 16+ |
| Message broker | Apache Kafka | 3.7+ (KRaft mode) |
| FHIR library | HAPI FHIR | 7.4.0 |

## Documentation

Detailed documentation is available in the [`docs/`](docs/) directory:

| Document | Description |
|----------|-------------|
| [Architecture](docs/architecture.md) | Service responsibilities, component design, and position within the CCE platform |
| [API Reference](docs/api-reference.md) | REST endpoints, request/response schemas, and status codes |
| [Flow Diagrams](docs/flow-diagrams.md) | Mermaid diagrams of the event ingestion, deduplication, and publishing pipelines |
| [Kafka Events](docs/kafka-events.md) | Topic definitions, message schemas, partitioning strategy, and sample payloads |
| [Data Dictionary](docs/data-dictionary.md) | Database tables, columns, enums, CloudEvents fields, and Kafka message fields |
| [Deployment Guide](docs/deployment-guide.md) | Local setup, Docker Compose, building from source, and configuration reference |
| [Operations Runbook](docs/operations-runbook.md) | Health checks, Kubernetes probes, monitoring, alerting, and troubleshooting |

## Quick Start

```bash
# Start infrastructure (PostgreSQL + Kafka)
docker compose up -d

# Build
./mvnw clean package

# Run
java -jar target/cce-collector-service-*.jar
```

See the [Deployment Guide](docs/deployment-guide.md) for full setup instructions.
