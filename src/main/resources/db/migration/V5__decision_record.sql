-- V5__decision_record.sql
-- Persistence for the UC-05 policy evaluation bounded context (application-layer.md
-- §6/§8/§10, use-cases.md §5.4, data-model.md §E).
--
-- DecisionRecord round-trips as two tables exactly as data-model.md §7
-- prescribes: the aggregate owns the scalar state and the 1:N DecisionReason
-- VOs are heavily FK'd to their parent (decision_reason). Evidence citations
-- are flattened to a comma-separated UUID list on the reason row
-- (never-query-by-column child payload, data-model.md §7, mirroring
-- risk_factor.evidence_reference_ids); RequiredAction enums flatten to a CSV
-- column on the parent.
--
-- Tenant isolation follows data-model.md §2/§5: tenant_id on every row and a
-- tenant-scoped FK back to analysis_run (whose (tenant_id, id) uniqueness was
-- declared in V3). One decision per analysis run (application-layer.md §10).

CREATE TABLE decision_record (
    id                UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    analysis_run_id   UUID         NOT NULL,
    risk_assessment_id UUID        NOT NULL,
    policy_id         UUID         NOT NULL,
    policy_version    VARCHAR(32)  NOT NULL,
    outcome           VARCHAR(24)  NOT NULL,
    required_actions  VARCHAR(255),
    generated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_decision_record PRIMARY KEY (id),
    CONSTRAINT uq_decision_record_tenant_analysis_run UNIQUE (tenant_id, analysis_run_id),
    CONSTRAINT fk_decision_record_analysis_run
        FOREIGN KEY (tenant_id, analysis_run_id) REFERENCES analysis_run (tenant_id, id),
    CONSTRAINT fk_decision_record_risk_assessment
        FOREIGN KEY (risk_assessment_id) REFERENCES risk_assessment (id)
);

CREATE TABLE decision_reason (
    id                     UUID          NOT NULL,
    tenant_id              UUID          NOT NULL,
    decision_record_id     UUID          NOT NULL,
    explanation            VARCHAR(1000) NOT NULL,
    rule_id                VARCHAR(64)   NOT NULL,
    evidence_reference_ids VARCHAR(1000),
    CONSTRAINT pk_decision_reason PRIMARY KEY (id),
    CONSTRAINT fk_decision_reason_decision_record
        FOREIGN KEY (decision_record_id) REFERENCES decision_record (id)
);

CREATE INDEX idx_decision_record_tenant ON decision_record (tenant_id);
CREATE INDEX idx_decision_reason_tenant ON decision_reason (tenant_id);
