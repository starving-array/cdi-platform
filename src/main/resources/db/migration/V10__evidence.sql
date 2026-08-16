-- V10__evidence.sql
-- Persistence for the P2 SearchEvidence capability (application-layer.md §3/§4,
-- use-cases.md §6.2 SearchEvidence, data-model.md §C, evidence-domain.md,
-- ADR-008). This is the FIRST V1.1/P2-point-2 capability and the first
-- evidence persistence migration: the deterministic SQL/lexical-first store
-- backing EvidenceSearchPort.searchByQuery (T1-approved mapping).
--
-- Column mapping (T1, ADR-008): the evidence_record table round-trips the
-- EvidenceRecord aggregate exactly. Searchable fields are `title`
-- (VARCHAR(255) NOT NULL) and `content` (TEXT NULL). `source_type` stores the
-- SourceType enum name; `source_uri` stores EvidenceSource.sourceReference (a
-- URI or unique string, data-model.md §C / evidence-domain.md §4); `origin`
-- stores the EvidenceOrigin enum name; `content_hash` is the SHA-256 hex digest
-- of content (VARCHAR(64) per the V2 commit_sha precedent); `captured_at` is
-- the immutable capture timestamp; `source_timestamp` and the Relevance pair
-- (`relevance_score` DOUBLE PRECISION, `relevance_reason`) are nullable
-- (Relevance is Optional in the domain).
--
-- DETERMINISTIC FIRST ONLY (ADR-008 B1): the search is a tenant-scoped SQL
-- ILIKE containment scan over title/content (ports-and-adapters.md §2.3 "SQL
-- ILIKE"). NO evidence_embedding table, NO pgvector extension, NO HNSW/IVFFlat
-- index, NO semantic infrastructure in this migration — semantic/vector search
-- stays deferred behind the same EvidenceSearchPort (application-layer.md
-- §13.5, ADR-003 Draft, data-model.md §C/§5/§10).
--
-- Tenant isolation (data-model.md §2/§5): tenant_id on the row and a
-- tenant-scoped FK to analysis_run (tenant_id, id) — the approved analysis_run
-- FK (mirrors fk_risk_assessment_analysis_run from V3). UNIQUE (tenant_id, id)
-- mirrors uq_change_tenant_id (V2) / uq_analysis_run_tenant_id (V3).
--
-- Audit fields (data-model.md §6): evidence is immutable historical fact; like
-- risk_assessment (V3, calculated_at) it does NOT add created_at/updated_at —
-- captured_at is the audit/fact timestamp. A future ingestion use case may
-- promote correlation_id if a consumer needs it (not added here).
--
-- Events: no domain event is published by this migration. EvidenceIngested
-- (named only in bounded-contexts.md §1) remains out of the canonical
-- domain-events.md §4 catalog — evidence ingestion is explicitly out of scope
-- for the SearchEvidence delivery (ADR-008).

CREATE TABLE evidence_record (
    id               UUID           NOT NULL,
    tenant_id        UUID           NOT NULL,
    analysis_run_id  UUID           NOT NULL,
    source_type      VARCHAR(32)    NOT NULL,
    source_uri       VARCHAR(512)   NOT NULL,
    origin           VARCHAR(32)    NOT NULL,
    title            VARCHAR(255)   NOT NULL,
    content          TEXT,
    content_hash     VARCHAR(64),
    captured_at      TIMESTAMPTZ    NOT NULL,
    source_timestamp TIMESTAMPTZ,
    relevance_score  DOUBLE PRECISION,
    relevance_reason VARCHAR(512),
    CONSTRAINT pk_evidence_record PRIMARY KEY (id),
    CONSTRAINT uq_evidence_record_tenant_id UNIQUE (tenant_id, id),
    -- Tenant-scoped FK per data-model.md §5: evidence can only reference an
    -- analysis_run of the same tenant, preventing cross-tenant joins.
    CONSTRAINT fk_evidence_record_analysis_run
        FOREIGN KEY (tenant_id, analysis_run_id) REFERENCES analysis_run (tenant_id, id)
);

CREATE INDEX idx_evidence_record_tenant ON evidence_record (tenant_id);
CREATE INDEX idx_evidence_record_analysis_run ON evidence_record (analysis_run_id);