-- V4__agent_investigation.sql
-- Persistence for the UC-04 InvestigateRisk bounded context (application-layer.md
-- §6 UC-04, use-cases.md §5.3, data-model.md §D/§F).
--
-- AgentInvestigation round-trips as two tables exactly as data-model.md §7
-- prescribes: the aggregate owns the scalar state and the 1:N
-- InvestigationFinding VOs are heavily FK'd to their parent
-- (investigation_finding). Evidence citations are flattened to a
-- comma-separated UUID list on the finding row (never-query-by-column child
-- payload, data-model.md §7, mirroring risk_factor.evidence_reference_ids).
--
-- Tenant isolation follows data-model.md §2/§5: tenant_id on every row and a
-- tenant-scoped FK back to analysis_run (whose (tenant_id, id) uniqueness was
-- declared in V3). One investigation per analysis run.

CREATE TABLE agent_investigation (
    id                 UUID         NOT NULL,
    tenant_id          UUID         NOT NULL,
    analysis_run_id    UUID         NOT NULL,
    change_id          UUID         NOT NULL,
    risk_assessment_id UUID         NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    failure_category   VARCHAR(32),
    failure_code       VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL,
    completed_at       TIMESTAMPTZ,
    CONSTRAINT pk_agent_investigation PRIMARY KEY (id),
    CONSTRAINT uq_agent_investigation_tenant_analysis_run UNIQUE (tenant_id, analysis_run_id),
    CONSTRAINT fk_agent_investigation_analysis_run
        FOREIGN KEY (tenant_id, analysis_run_id) REFERENCES analysis_run (tenant_id, id)
);

CREATE TABLE investigation_finding (
    id                     UUID         NOT NULL,
    tenant_id              UUID         NOT NULL,
    agent_investigation_id UUID         NOT NULL,
    summary                VARCHAR(500) NOT NULL,
    explanation            VARCHAR(4000),
    evidence_citation_ids  VARCHAR(1000),
    CONSTRAINT pk_investigation_finding PRIMARY KEY (id),
    CONSTRAINT fk_investigation_finding_agent_investigation
        FOREIGN KEY (agent_investigation_id) REFERENCES agent_investigation (id)
);

CREATE INDEX idx_agent_investigation_tenant ON agent_investigation (tenant_id);
CREATE INDEX idx_investigation_finding_tenant ON investigation_finding (tenant_id);
