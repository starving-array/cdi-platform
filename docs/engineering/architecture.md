# CDI System Architecture & Engineering Deep-Dive

---

## 1. System Architecture Principles

The Change Decision Intelligence (CDI) platform is constructed around **Domain-Driven Design (DDD)** and **Hexagonal (Ports and Adapters)** architectural paradigms:

1. **Separation of Risk & Decision**:
   - `RiskAssessment` represents an *objective calculation* of failure probability based on context.
   - `DecisionRecord` represents a *subjective policy enforcement* applying tenant rules to that risk.
2. **Immutability of Audit Records**:
   - Risk assessments, decisions, evidence records, and deployment outcomes are append-only audit artifacts. Once created, they are never updated or mutated.
3. **Strict Aggregate Boundaries**:
   - Aggregate roots manage invariants strictly within their boundary (e.g. `Change` references `ServiceId` and `CommitSnapshot`, but does not contain JPA entities from other aggregates).
4. **Command / Query Separation (CQS)**:
   - Command handlers perform business validations, enforce tenant invariants, and persist domain events/entities.
   - Query services execute performant, tenant-isolated lookups without mutating state.
5. **No Cross-Tenant Contamination**:
   - Every table and entity enforces `tenant_id`. Composite unique indexes guarantee tenant boundaries at the database constraint level.

---

## 2. Bounded Context Map

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Organization   │       │  System Context │       │     Policy      │
│     (Tenant)    │       │    (Service)    │       │ (Rule Engine)   │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         ▼                         ▼                         ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│     Change      │──────▶│ Risk Assessment │──────▶│ Decision Engine │
│ (AnalysisRun)   │       │(Deterministic)  │       │(Human Override) │
└────────┬────────┘       └────────▲────────┘       └────────▲────────┘
         │                         │ (Vector Search)         │
         │                ┌────────┴────────┐                │
         │                │ Evidence Memory │                │
         │                │ (pgvector/ONNX) │                │
         │                └─────────────────┘                │
         ▼                                                   │
┌─────────────────┐                                          │
│   Deployment    │──────────────────────────────────────────┘
│(Outcome Tracking│──────────────▶ [Decision Attribution]
└─────────────────┘
```

---

## 3. Persistence & Database Architecture

CDI uses **PostgreSQL 16** with the **`pgvector`** extension. Flyway manages schema evolution across 14 versioned migrations:

- `V1` – Base schema setup.
- `V6` – Tenant / Organization aggregate.
- `V7` – Repository metadata and provider tracking.
- `V8` – Service aggregate, dependencies, and criticality tiers.
- `V9` – Versioned deterministic Policy engine & rules (`UNIQUE (tenant_id) WHERE status = 'ACTIVE'`).
- `V10` – Evidence records and source provenance.
- `V11` – Decision records and Human Override audit tracking.
- `V12` – `pgvector` extension, `evidence_embedding` table, HNSW cosine index.
- `V13` – Deployment aggregate and DeploymentOutcome tracking.
- `V14` – Observational Decision Attribution table & telemetry index.

---

## 4. Semantic Vector & Embedding Architecture

The system uses an application-level abstraction:
- **`EmbeddingPort`**: Defines `generateEmbedding(String text)` returning `float[]` and `getModelVersion()`.
- **`OnnxEmbeddingAdapter`**: Production adapter executing `sentence-transformers/all-MiniLM-L6-v2` locally using Microsoft ONNX Runtime Java. Runs embedded within the JVM with zero external API calls or network latency.
- **pgvector Integration**: Stores 384-dimensional normalized vectors in `evidence_embedding` indexed via `vector_cosine_ops` HNSW for high-throughput cosine similarity retrieval.

---

## 5. Deployment & Attribution Pipeline

1. **Deployment Intake (`V13`)**:
   - Ingests software release events tied to a `ServiceId` and `commit_sha`.
   - Supports idempotency via `(tenant_id, external_deployment_id)`.
2. **Outcome Telemetry (`V13`)**:
   - Appends verified outcomes (`SUCCESS`, `FAILURE`, `INCIDENT`, `ROLLED_BACK`) with optional incident tracking references.
3. **Observational Attribution (`V14`)**:
   - Correlates `DeploymentOutcome` $\rightarrow$ `Deployment` $\rightarrow$ `(tenant_id, commit_sha)` $\rightarrow$ `AnalysisRun` $\rightarrow$ `RiskAssessment` & `DecisionRecord`.
   - Computes prediction accuracy:
     - `ACCURATE_LOW_RISK`: Predicted Low/Medium risk $\rightarrow$ Succeeded in production.
     - `ACCURATE_HIGH_RISK`: Predicted High/Critical risk $\rightarrow$ Failed / Rolled back / Incident.
     - `UNDERESTIMATED_RISK`: Predicted Low/Medium risk $\rightarrow$ Failed / Incident (False Negative).
     - `OVERESTIMATED_RISK`: Predicted High/Critical risk $\rightarrow$ Succeeded (False Positive).
     - `UNATTRIBUTED`: Commit had no prior evaluation run or missing risk score.

---

## 6. Testing & Quality Assurance

- **Pure Domain Unit Tests**: In-memory unit tests verifying aggregate root invariants, state machines, and mathematical formulas without Spring overhead.
- **Persistence & Integration Tests**: Backed by PostgreSQL Testcontainers running `pgvector/pgvector:pg16`, asserting real SQL migrations, transaction semantics, foreign keys, and HNSW indexes.
- **REST WebMvc Tests**: Testing HTTP request parsing, status codes, payload serialization, and exception mapping.