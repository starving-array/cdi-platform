-- V6__tenant.sql
-- Persistence for the UC-07 CreateOrganization bounded context (application-layer.md
-- §6 UC-07, bounded-contexts.md §1, data-model.md §3.A).
--
-- The tenant table is the persistence root for the Organization aggregate
-- (organization-repository-service-domain.md: Organization is the enterprise
-- tenant). It is the documented multi-tenant isolation special case
-- (data-model.md §2): every other table carries a tenant_id FK, but the tenant
-- row IS the tenant — it cannot reference a tenant_id column on itself.
-- Therefore no tenant_id column and no self-referential FK are declared here.
--
-- Audit fields (data-model.md §6): created_at and updated_at on every row.
-- The Organization domain currently exposes only createdAt (no updatedAt field);
-- the JPA entity carries updated_at and initializes it to created_at on insert.
-- A future use case that exercises suspend/reactivate through the application
-- layer can promote updated_at into the domain at that time.
--
-- UNIQUE(name) is the natural-key duplicate guard and the race-safe final
-- idempotency guarantee for UC-07 (application-layer.md §10): the handler
-- checks findByName first (idempotent reuse), and the DB constraint prevents
-- any concurrent duplicate insert from creating a second tenant row.

CREATE TABLE tenant (
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_name UNIQUE (name)
);
