-- V14__decision_attribution.sql
-- Observational correlation table for Decision Attribution & Risk Feedback (D-ATTR-1, D-ATTR-2).

CREATE TABLE decision_attribution (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    deployment_id           UUID            NOT NULL,
    service_id              UUID            NOT NULL,
    analysis_run_id         UUID,
    risk_assessment_id      UUID,
    decision_record_id      UUID,
    classification          VARCHAR(32)     NOT NULL,
    deployment_outcome      VARCHAR(32)     NOT NULL,
    predicted_risk_level    VARCHAR(16),
    decision_outcome        VARCHAR(32),
    has_human_override      BOOLEAN         NOT NULL DEFAULT FALSE,
    attributed_at           TIMESTAMPTZ     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_decision_attribution PRIMARY KEY (id),
    CONSTRAINT uq_decision_attribution_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_decision_attribution_deployment UNIQUE (tenant_id, deployment_id),
    CONSTRAINT fk_decision_attribution_deployment
        FOREIGN KEY (deployment_id)
        REFERENCES deployment (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_decision_attribution_tenant ON decision_attribution (tenant_id);
CREATE INDEX idx_decision_attribution_service ON decision_attribution (service_id);
CREATE INDEX idx_decision_attribution_classification ON decision_attribution (classification);
CREATE INDEX idx_decision_attribution_attributed_at ON decision_attribution (attributed_at);