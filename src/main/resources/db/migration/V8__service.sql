-- V8__service.sql
-- Persistence for the UC-09 CreateService bounded context (application-layer.md
-- §6 UC-09, bounded-contexts.md §3, data-model.md §3.A).
--
-- Service is a tenant-scoped child aggregate: each row carries a tenant_id
-- that references the tenant table (V6, UC-07). Like the Repository (V7,
-- UC-08) and unlike the Organization/tenant root (which is its own tenant
-- identity and therefore has no tenant_id column), a Service belongs to a
-- tenant and must be looked up within the tenant scope (application-layer.md
-- §13.3, data-model.md §2).
--
-- Natural-key (application-layer.md §10): (tenant_id, name) is the business
-- identity of a system-context service — a deployable unit's human-readable
-- name scoped per tenant. UNIQUE (tenant_id, name) is the race-safe final
-- idempotency guarantee for UC-09: the handler checks for an existing row
-- first (idempotent reuse, created=false, no save, no event); the DB
-- constraint prevents any concurrent duplicate insert from creating a second
-- service row.
--
-- Tenant isolation (data-model.md §2/§5): FK(tenant_id) REFERENCES tenant(id)
-- requires the tenant to exist. For UC-09 the tenant is assumed pre-existing
-- (auth upstream + V6); a non-existent tenant surfaces as a FK violation that
-- propagates per the existing persistence-exception convention. No
-- TENANT_NOT_FOUND application error is introduced (closed ApplicationError
-- catalog, UC-09 contract).
--
-- Audit fields (data-model.md §6): created_at and updated_at on every row. The
-- Service domain constructor exposes only createdAt; the JPA entity carries
-- updated_at and initializes it to created_at on insert by the mapper (the
-- same precedent used by OrganizationEntity for the tenant table, V6).
--
-- The owner column is nullable, mirroring the Service domain constructor which
-- accepts a null owner (Service.java: assigns owner only if non-null). The
-- criticality_tier column is VARCHAR(16) to match the V7/V6 enum-string
-- precedent and to fit the longest CriticalityTier name ("TIER_0".."TIER_3").
--
-- IMPORTANT: this migration does NOT add a service.repository_id column. The
-- Service domain has an optional repositoryId association set via
-- associateWithRepository(repoId), but UC-09 does not exercise that mutation
-- (only CreateService is in scope). Adding such a column now would be
-- speculative (YAGNI) and would mirror the UC-08 decision to NOT add a
-- change.repository_id FK at V7. A future use case that exercises
-- associateWithRepository will own a V9+ migration that adds the column and
-- any required FK.
--
-- IMPORTANT: this migration does NOT create a service_dependency table. The
-- Service domain has an addDependency(target) method, but UC-09 does not
-- exercise that mutation. data-model.md §3.A mentions the service_dependency
-- edge table narratively for a future use case; adding it now with no use
-- case to exercise it would be speculative (YAGNI) — same precedent used by
-- UC-08 (which did not create a Change.repository FK).
--
-- No new domain event is published. UC-09 follows the UC-08 precedent: no
-- ServiceCreated event exists; the bounded-contexts.md §3 narrative lists
-- ServiceCriticalityUpdated / DependencyChanged for OTHER future service
-- operations (criticality changes, dependency changes), not for creation, and
-- those events are not in the canonical domain-events.md §4 catalog.

CREATE TABLE service (
    id                UUID           NOT NULL,
    tenant_id         UUID           NOT NULL,
    name              VARCHAR(255)   NOT NULL,
    criticality_tier  VARCHAR(16)    NOT NULL,
    owner             VARCHAR(255),
    status            VARCHAR(16)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_service PRIMARY KEY (id),
    CONSTRAINT uq_service_tenant_name
        UNIQUE (tenant_id, name),
    CONSTRAINT fk_service_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX idx_service_tenant ON service (tenant_id);
