-- V13__deployment_tracking.sql
-- Persistence for Deployment and DeploymentOutcome tracking (D-DEP-1 to D-DEP-4).

-- 1. Deployment table
CREATE TABLE deployment (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    service_id              UUID            NOT NULL,
    commit_sha              VARCHAR(64)     NOT NULL,
    environment             VARCHAR(32)     NOT NULL,
    status                  VARCHAR(32)     NOT NULL,
    external_deployment_id  VARCHAR(128),
    deployed_at             TIMESTAMPTZ     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_deployment PRIMARY KEY (id),
    CONSTRAINT uq_deployment_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_deployment_tenant_external UNIQUE (tenant_id, external_deployment_id),
    CONSTRAINT fk_deployment_service
        FOREIGN KEY (service_id) 
        REFERENCES service (id)
);

CREATE INDEX idx_deployment_tenant ON deployment (tenant_id);
CREATE INDEX idx_deployment_service ON deployment (service_id);
CREATE INDEX idx_deployment_commit_sha ON deployment (commit_sha);
CREATE INDEX idx_deployment_deployed_at ON deployment (deployed_at);

-- 2. Deployment outcome table
CREATE TABLE deployment_outcome (
    id                      UUID            NOT NULL,
    tenant_id               UUID            NOT NULL,
    deployment_id           UUID            NOT NULL,
    outcome                 VARCHAR(32)     NOT NULL,
    incident_reference      VARCHAR(255),
    recorded_at             TIMESTAMPTZ     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_deployment_outcome PRIMARY KEY (id),
    CONSTRAINT uq_deployment_outcome_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_deployment_outcome_deployment UNIQUE (tenant_id, deployment_id),
    CONSTRAINT fk_deployment_outcome_deployment
        FOREIGN KEY (deployment_id) 
        REFERENCES deployment (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_deployment_outcome_tenant ON deployment_outcome (tenant_id);
CREATE INDEX idx_deployment_outcome_deployment ON deployment_outcome (deployment_id);