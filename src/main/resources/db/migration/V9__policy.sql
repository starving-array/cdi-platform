-- V9__policy.sql
-- Persistence for the UC-10 CreatePolicy bounded context (application-layer.md
-- §6 UC-10, bounded-contexts.md §7, data-model.md §E, policy-decision-domain.md
-- §2/§3).
--
-- Policy is a tenant-scoped child aggregate: each row carries a tenant_id that
-- references the tenant table (V6, UC-07). Like the Repository (V7, UC-08) and
-- the Service (V8, UC-09) and unlike the Organization/tenant root (which is its
-- own tenant identity and therefore has no tenant_id column), a Policy belongs
-- to a tenant and must be looked up within the tenant scope
-- (application-layer.md §13.3, data-model.md §2).
--
-- Natural-key (resolved audit Decision 1): exactly ONE ACTIVE policy per
-- tenant. The natural key is (tenant_id) for the active row; the policy `name`
-- is descriptive metadata, not a UNIQUE key. The PostgreSQL partial unique
-- index UNIQUE (tenant_id) WHERE status = 'ACTIVE' is the race-safe final
-- idempotency guarantee for UC-10: the handler checks for an existing active
-- row first (idempotent reuse, created=false, no save, no event); the partial
-- unique constraint prevents any concurrent duplicate insert from creating a
-- second active policy for the same tenant, while still allowing archived
-- (status='ARCHIVED') historical versions to coexist (policy-decision-domain.md
-- §3: policies are strictly versioned; historical versions are retained for
-- traceability). The `version` column stores the PolicyVersion label (e.g.
-- 'v1'); the `status` column stores the PolicyStatus enum name.
--
-- Tenant isolation (data-model.md §2/§5): FK(tenant_id) REFERENCES tenant(id)
-- requires the tenant to exist. For UC-10 the tenant is assumed pre-existing
-- (auth upstream + V6); a non-existent tenant surfaces as a FK violation that
-- propagates per the existing persistence-exception convention. No
-- TENANT_NOT_FOUND application error is introduced (closed ApplicationError
-- catalog, UC-10 contract — same precedent as UC-08/UC-09).
--
-- Audit fields (data-model.md §6): created_at and updated_at on every row. The
-- Policy domain constructor exposes only createdAt; the JPA entity carries
-- updated_at and initializes it to created_at on insert by the mapper (the
-- same precedent used by OrganizationEntity/ServiceEntity for V6/V8).
--
-- The 1:N child policy_rule table (data-model.md §E) stores the deterministic
-- match rules. Each rule persists the actual PolicyRule record shape
-- (policy-decision-domain.md §4: no CEL/OPA — rules are match sets, not
-- expression strings). The Set<Enum> match sets (target_tiers,
-- target_risk_levels, required_evidence_states) and the action list flatten
-- to comma-separated enum-name CSVs in their respective columns
-- (data-model.md §7 — same never-query-by-column precedent as
-- risk_factor.evidence_reference_ids). The rule_id is the domain PolicyRule
-- identifier (not the surrogate PK).
--
-- IMPORTANT (resolved audit Decision 2): this migration does NOT add a
-- decision_record.policy_id -> policy.id foreign key. V5 (decision_record)
-- is frozen with policy_id as an opaque UUID reference, preserving historical
-- traceability via the persisted policy_version string. This mirrors the UC-08
-- decision to NOT add a change.repository_id FK at V7 (a frozen-baseline
-- non-goal, by precedent). A future use case that warrants the FK can add it
-- in V10+.
--
-- No new domain event is published. UC-10 follows the UC-08/UC-09 precedent:
-- no PolicyCreated/PolicyUpdated event exists in the canonical
-- domain-events.md §4 catalog (resolved audit Decision 3;
-- application-layer.md §12 "publish only what has a consumer or audit need").

CREATE TABLE policy (
    id           UUID         NOT NULL,
    tenant_id    UUID         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(1000),
    status       VARCHAR(16)  NOT NULL,
    version      VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_policy PRIMARY KEY (id),
    CONSTRAINT fk_policy_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

-- Partial unique: at most one ACTIVE policy per tenant (resolved audit
-- Decision 1). ARCHIVED historical versions may coexist for the same tenant.
CREATE UNIQUE INDEX uq_policy_tenant_active
    ON policy (tenant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_policy_tenant ON policy (tenant_id);

CREATE TABLE policy_rule (
    id                        UUID          NOT NULL,
    tenant_id                 UUID          NOT NULL,
    policy_id                 UUID          NOT NULL,
    rule_id                   VARCHAR(64)   NOT NULL,
    target_tiers              VARCHAR(255),
    target_risk_levels        VARCHAR(255),
    required_evidence_states  VARCHAR(255),
    outcome                   VARCHAR(24)   NOT NULL,
    actions                   VARCHAR(255),
    explanation               VARCHAR(1000) NOT NULL,
    CONSTRAINT pk_policy_rule PRIMARY KEY (id),
    CONSTRAINT fk_policy_rule_policy
        FOREIGN KEY (policy_id) REFERENCES policy (id),
    CONSTRAINT fk_policy_rule_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX idx_policy_rule_tenant ON policy_rule (tenant_id);
CREATE INDEX idx_policy_rule_policy ON policy_rule (policy_id);
