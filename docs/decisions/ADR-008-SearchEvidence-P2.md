# ADR 008: SearchEvidence (P2) — Deterministic Query-First Evidence Search

## Status
Accepted (2026-08-16) — the five SearchEvidence decisions (D1, D2, D3, D5, this ADR) were approved by product/architecture before implementation.

## Context
`use-cases.md` §6.2 defines SearchEvidence as a synchronous, ENGINEER-only query
(`query` + filters → `List<EvidenceRecord>`, port `EvidenceSearchPort`). It is the
**first P2 capability** (`application-layer.md` §3/§4 enumerations — after the
canonical V1 inventory which ends at UC-18) and is explicitly **not** a V1 UC and
**not** "UC-19". Two design frictions had to be resolved before implementation:

1. `application-layer.md` §6:297 states "queries never call external ports", yet
   `use-cases.md` §6.2 names `EvidenceSearchPort` for this query.
2. Search semantics/scope (filters, limit, pagination, failure, ordering) were
   under-specified, and no evidence persistence existed yet.

## Decision
- **Sanctioned exception (B5/architecture)**: the SearchEvidence query calls the
  read-only `EvidenceSearchPort.searchByQuery(...)` — a proven exception to
  "queries never call external ports", grounded in the read-only, synchronous
  port and its graceful-degradation contract (`ports-and-adapters.md` §4,
  `application-layer.md` §13.5 port-extension sanction). Existing
  `searchSimilarChanges` / `searchIncidents` are untouched.
- **P2-first capability**: delivered as the first P2 item, with no V1 UC number.
- **ENGINEER-only authorization** (`use-cases.md` §6.2); any other role →
  `UNAUTHORIZED`.
- **D1 — query-only first delivery**: filters are structurally reserved but
  empty; no filter names, values, or semantics are invented.
- **D2 — limit**: default 20 (the EVIDENCE_LIMIT code convention), caller-
  overridable, no additional cap.
- **D3 — top-N only**: no pagination, offset, or cursor.
- **D5 — response container**: `SearchEvidenceResult(boolean degraded,
  List<EvidenceRecord> records)`. A synchronous search failure returns
  zero/partial records with `degraded=true`; the API layer maps that to
  `EVIDENCE_UNAVAILABLE`. `EVIDENCE_COLLECTION_FAILED` stays reserved for the
  async/terminal worker path; `ApplicationError` is not modified.
- **T1 — mapping**: adopt the domain model — `title VARCHAR(255) NOT NULL`,
  `content TEXT NULL` are the searchable fields (kept alongside `source_uri`,
  `origin`, `content_hash`, `captured_at`, `source_timestamp`, `relevance`).
  API `summary` is rendered from domain `title` (`summary := title`,
  backward-compatible meaning).
- **B1 — deterministic first**: SQL/lexical (`ILIKE`) over title/content,
  tenant-scoped; semantic/vector retrieval stays deferred behind the same
  `EvidenceSearchPort`. V10 adds **no** `evidence_embedding`, pgvector, HNSW,
  or IVFFlat infrastructure.
- **B3 — ordering**: `capturedAt` ASC with `EvidenceId.value()` (UUID) as the
  deterministic tie-breaker.
- **B4 — degradation**: sync failure → zero evidence, `degraded=true`, no
  retry; `EVIDENCE_UNAVAILABLE` semantics at the API layer.
- **Persistence prerequisite**: `V10__evidence.sql` (T1 mapping, tenant-scoped
  FK to `analysis_run (tenant_id, id)`, `UNIQUE (tenant_id, id)`) plus an
  `EvidenceRepository` (save/seed) and the deterministic JPA adapter. Evidence
  ingestion (and the `EvidenceIngested` event) is explicitly **out of scope**
  for this delivery — no new domain event is published.

## Consequences
- The "queries never call external ports" rule now has one documented exception
  (this query), noted in `application-layer.md` §6 and on the port itself.
- Search is deterministic, cheap, and tenant-isolated from day one; a future
  semantic swap happens behind the same port (`application-layer.md` §13.5).
- `V10` is the first evidence persistence migration and the base for a future
  ingestion use case.
- The future SearchEvidence API endpoint reuses the aggregate evidence payload
  rendering (`summary := title`), so no new field is invented there either.