-- V2__change_and_analysis_run.sql
-- Persistence for the UC-01 vertical slice (application-layer.md §6 UC-01,
-- §11.5, data-model.md §B).
--
-- SCHEMA DECISION (documented in docs/06-development/application-foundation.md):
-- data-model.md §B lists a simplified `change` shape (id, tenant_id, repo_uri,
-- pr_id, status). The implemented Change aggregate tracks more state that must
-- round-trip for UC-01 idempotency (application-layer.md §6: duplicate detection
-- compares `latestCommitSha`), so the table carries the full aggregate columns.
-- The tenant-isolation, uniqueness, and tenant-scoped FK rules from §2/§5 are
-- preserved exactly.

CREATE TABLE change (
    id                 UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    repository_id      UUID        NOT NULL,
    provider_change_id VARCHAR(255) NOT NULL,
    title              VARCHAR(255) NOT NULL,
    description        VARCHAR(2000) NOT NULL,
    author             VARCHAR(255) NOT NULL,
    source_branch      VARCHAR(255) NOT NULL,
    target_branch      VARCHAR(255) NOT NULL,
    latest_commit_sha  VARCHAR(64)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_change PRIMARY KEY (id),
    CONSTRAINT uq_change_tenant_repo_provider UNIQUE (tenant_id, repository_id, provider_change_id),
    CONSTRAINT uq_change_tenant_id UNIQUE (tenant_id, id)
);

-- Analysis is bound to one exact commit snapshot (domain-model.md §B invariant).
-- UNIQUE (tenant_id, change_id, commit_sha) is the final idempotency safety net
-- (data-model.md §5, application-layer.md §10/§11.5).
CREATE TABLE analysis_run (
    id                UUID        NOT NULL,
    tenant_id         UUID        NOT NULL,
    change_id         UUID        NOT NULL,
    commit_sha        VARCHAR(64) NOT NULL,
    branch            VARCHAR(255) NOT NULL,
    status            VARCHAR(16) NOT NULL,
    failure_category  VARCHAR(32),
    failure_code      VARCHAR(255),
    failed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT pk_analysis_run PRIMARY KEY (id),
    CONSTRAINT uq_analysis_run_tenant_change_commit UNIQUE (tenant_id, change_id, commit_sha),
    -- Tenant-scoped FK per data-model.md §5: a run can only reference a change
    -- of the same tenant, preventing cross-tenant joins at the DB level.
    CONSTRAINT fk_analysis_run_change
        FOREIGN KEY (tenant_id, change_id) REFERENCES change (tenant_id, id)
);

CREATE INDEX idx_change_tenant ON change (tenant_id);
CREATE INDEX idx_analysis_run_tenant ON analysis_run (tenant_id);