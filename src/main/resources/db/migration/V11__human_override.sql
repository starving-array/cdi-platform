-- V11__human_override.sql
-- Persistence for the UC-06 OverrideDecision bounded context (application-layer.md
-- §6 UC-06/§13.2, ADR-006, bounded-contexts.md §6, data-model.md §E,
-- policy-decision-domain.md §10).
--
-- Frozen D1: a separate additive human_override table as a 1:1 child of the
-- immutable decision_record. The historical decision row is NEVER modified by an
-- override (policy-decision-domain.md §12: "DecisionRecord objects have no
-- setters ... permanent historical artifact"); the override is a supplemental
-- record referencing it. Approved UC-06 D1/D2 contract.
--
-- Column mapping (frozen D1, HumanOverride VO from policy-decision-domain.md
-- §10): tenant_id (data-model.md §2 tenant-on-every-row), decision_record_id
-- (the overridden decision), original_outcome / new_outcome (the HumanOverride
-- outcomes), actor_id (the TENANT_ADMIN actor), justification (required - the
-- override is never silent), override_at (the override timestamp, supplemental
-- to decision_record.generated_at), created_at / updated_at (data-model.md §6
-- audit fields, initialized to override_at on insert per the V9 policy mapper
-- precedent).
--
-- Exactly one override per decision (frozen D2): the domain guard in
-- DecisionRecord.withOverride rejects a second override, and
-- UNIQUE (tenant_id, decision_record_id) is the race-safe DB backstop that makes
-- a duplicate override row impossible even under a concurrent race.
--
-- Tenant isolation (data-model.md §2/§5): tenant_id on the row with a tenant
-- index, and the tenant-scoped UNIQUE; the FK to decision_record(id) mirrors the
-- V5 decision_reason child precedent (the child carries tenant_id + tenant index
-- but is FK-bound to its parent aggregate row).
--
-- No existing migration is modified; V11 is the only new migration.

CREATE TABLE human_override (
    id                 UUID          NOT NULL,
    tenant_id          UUID          NOT NULL,
    decision_record_id UUID          NOT NULL,
    original_outcome   VARCHAR(24)   NOT NULL,
    new_outcome        VARCHAR(24)   NOT NULL,
    actor_id           VARCHAR(255)  NOT NULL,
    justification      VARCHAR(1000) NOT NULL,
    override_at        TIMESTAMPTZ   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_human_override PRIMARY KEY (id),
    CONSTRAINT uq_human_override_tenant_decision UNIQUE (tenant_id, decision_record_id),
    CONSTRAINT fk_human_override_decision_record
        FOREIGN KEY (decision_record_id) REFERENCES decision_record (id)
);

CREATE INDEX idx_human_override_tenant ON human_override (tenant_id);