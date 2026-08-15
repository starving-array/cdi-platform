-- V3__risk_assessment.sql
-- Persistence for the UC-03 vertical slice (application-layer.md §6 UC-03,
-- data-model.md §D).
--
-- RiskAssessment round-trips as two tables exactly as data-model.md §7
-- prescribes: the aggregate owns the scalar state and the 1:N RiskFactor VOs
-- are heavily FK'd to their parent (risk_factor). Scoring is immutable audit
-- data stamped with the rule set version (risk-engine.md §6) and the
-- EvidenceState records degraded retrieval (risk-engine.md §8).
--
-- Tenant isolation follows data-model.md §2/§5: tenant_id on every row and a
-- tenant-scoped FK back to analysis_run (whose (tenant_id, id) uniqueness is
-- declared here, mirroring uq_change_tenant_id from V2). One assessment per
-- analysis run.

-- Analysis_run needs (tenant_id, id) uniqueness to serve as the FK target of
-- the tenant-scoped risk_assessment reference (data-model.md §5).
ALTER TABLE analysis_run
    ADD CONSTRAINT uq_analysis_run_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE risk_assessment (
    id                 UUID        NOT NULL,
    tenant_id          UUID        NOT NULL,
    analysis_run_id    UUID        NOT NULL,
    risk_score         INT         NOT NULL,
    risk_level         VARCHAR(16) NOT NULL,
    evidence_state     VARCHAR(32) NOT NULL,
    assessment_version VARCHAR(16) NOT NULL,
    calculated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_risk_assessment PRIMARY KEY (id),
    CONSTRAINT uq_risk_assessment_tenant_analysis_run UNIQUE (tenant_id, analysis_run_id),
    CONSTRAINT fk_risk_assessment_analysis_run
        FOREIGN KEY (tenant_id, analysis_run_id) REFERENCES analysis_run (tenant_id, id)
);

CREATE TABLE risk_factor (
    id                   UUID         NOT NULL,
    tenant_id            UUID         NOT NULL,
    risk_assessment_id   UUID         NOT NULL,
    factor_type          VARCHAR(32)  NOT NULL,
    contribution         INT          NOT NULL,
    explanation          VARCHAR(2000) NOT NULL,
    evidence_reference_ids VARCHAR(1000),
    CONSTRAINT pk_risk_factor PRIMARY KEY (id),
    CONSTRAINT fk_risk_factor_risk_assessment
        FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessment (id)
);

CREATE INDEX idx_risk_assessment_tenant ON risk_assessment (tenant_id);
CREATE INDEX idx_risk_factor_tenant ON risk_factor (tenant_id);