-- V12__pgvector_semantic_evidence.sql
-- Enables PostgreSQL pgvector extension and creates the evidence_embedding table
-- for semantic retrieval (ADR-003, ADR-008, data-model.md §3.C, D1-D5 architectural freeze).

-- 1. Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Create evidence_embedding table
CREATE TABLE evidence_embedding (
    id                  UUID            NOT NULL,
    tenant_id           UUID            NOT NULL,
    evidence_record_id  UUID            NOT NULL,
    chunk_index         INTEGER         NOT NULL DEFAULT 0,
    embedding           vector(384)     NOT NULL,
    model_version       VARCHAR(64)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_evidence_embedding PRIMARY KEY (id),
    CONSTRAINT uq_evidence_embedding_tenant_evidence_chunk 
        UNIQUE (tenant_id, evidence_record_id, chunk_index),
    CONSTRAINT fk_evidence_embedding_evidence
        FOREIGN KEY (tenant_id, evidence_record_id) 
        REFERENCES evidence_record (tenant_id, id) 
        ON DELETE CASCADE
);

-- 3. Tenant and lookup indexes
CREATE INDEX idx_evidence_embedding_tenant ON evidence_embedding (tenant_id);
CREATE INDEX idx_evidence_embedding_record ON evidence_embedding (evidence_record_id);

-- 4. HNSW cosine distance index for fast approximate nearest neighbor semantic search
CREATE INDEX idx_evidence_embedding_hnsw_cosine 
    ON evidence_embedding 
    USING hnsw (embedding vector_cosine_ops);