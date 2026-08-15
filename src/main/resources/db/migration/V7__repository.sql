-- V7__repository.sql
-- Persistence for the UC-08 CreateRepository bounded context (application-layer.md
-- §6 UC-08, bounded-contexts.md §2, data-model.md §3, §5).
--
-- Repository is a tenant-scoped child aggregate: each row carries a tenant_id
-- that references the tenant table (V6, UC-07). Unlike the organization/tenant
-- root (which is its own tenant identity and therefore has no tenant_id column),
-- a Repository belongs to a tenant and must be looked up within the tenant
-- scope (application-layer.md §13.3, data-model.md §2).
--
-- Natural-key (application-layer.md §10): (tenant_id, provider_type, external_id)
-- is the business identity of a source-control repository — the provider's stable
-- id, scoped per tenant. UNIQUE(tenant_id, provider_type, external_id) is the
-- race-safe final idempotency guarantee for UC-08: the handler checks for an
-- existing row first (idempotent reuse, created=false, no save, no event); the
-- DB constraint prevents any concurrent duplicate insert from creating a second
-- repository row.
--
-- Tenant isolation (data-model.md §2/§5): FK(tenant_id) REFERENCES tenant(id)
-- requires the tenant to exist. For UC-08 the tenant is assumed pre-existing
-- (auth upstream + V6); a non-existent tenant surfaces as a FK violation that
-- propagates per the existing persistence-exception convention. No
-- TENANT_NOT_FOUND application error is introduced (closed ApplicationError
-- catalog, UC-08 contract).
--
-- Audit fields (data-model.md §6): created_at and updated_at on every row. The
-- Repository domain constructor initializes updatedAt = createdAt; the
-- V7-validating JPA entity (ddl-auto: validate) carries an updated_at column
-- initialized to created_at on insert by the mapper.
--
-- IMPORTANT: this migration does NOT add change.repository_id -> repository.id.
-- The change table (V2) references repositories by an opaque RepositoryId UUID
-- column with no FK constraint, by design — UC-01/ProposeChange and its tests
-- create Change rows with freshly-generated RepositoryId values that do not
-- reference any repository row. Adding such a FK here would break the frozen
-- UC-01/UC-06 baseline. That linkage is an explicit non-goal for UC-08.

CREATE TABLE repository (
    id              UUID           NOT NULL,
    tenant_id       UUID           NOT NULL,
    provider_type   VARCHAR(16)    NOT NULL,
    external_id     VARCHAR(255)   NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    url             VARCHAR(2000)  NOT NULL,
    default_branch  VARCHAR(255)   NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_repository PRIMARY KEY (id),
    CONSTRAINT uq_repository_tenant_provider_external
        UNIQUE (tenant_id, provider_type, external_id),
    CONSTRAINT fk_repository_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX idx_repository_tenant ON repository (tenant_id);
