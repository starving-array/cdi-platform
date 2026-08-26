# Deployment Tracking & Observational Decision Attribution

---

## 1. Deployment Lifecycle & Domain Model

CDI tracks real-world software releases as first-class domain entities:

- **`Deployment` (Aggregate Root)**:
  - `id`: Unique `DeploymentId`.
  - `tenant_id`: Multi-tenant ownership.
  - `service_id`: Target deployable service.
  - `commit_sha`: Exact git commit SHA deployed.
  - `environment`: e.g. `production`, `staging`.
  - `status`: `IN_PROGRESS`, `SUCCESSFUL`, `FAILED`, `ROLLED_BACK`.
  - `external_deployment_id`: Optional CI/CD pipeline identifier for idempotency.
  - `deployed_at`, `created_at`.

- **`DeploymentOutcome` (Child Entity)**:
  - `outcome`: `SUCCESS`, `FAILURE`, `INCIDENT`, `ROLLED_BACK`.
  - `incident_reference`: Optional tracking ID (e.g. `INC-9821`).
  - `recorded_at`, `created_at`.

---

## 2. Observational Decision Attribution

Decision Attribution establishes a closed feedback loop by correlating production outcomes with pre-merge risk assessments and policy verdicts:

```
DeploymentOutcome (SUCCESS / FAILURE / INCIDENT / ROLLED_BACK)
       │
       ▼
Deployment (tenant_id, service_id, commit_sha, environment)
       │
       ▼ [correlate via tenant_id + commit_sha]
AnalysisRun (tenant_id, change_id, commit_sha)
       │
       ├──▶ RiskAssessment (score, risk_level: LOW/MEDIUM/HIGH/CRITICAL)
       └──▶ DecisionRecord (verdict: APPROVE / BLOCK / REVIEW_REQUIRED)
```

### Classification Matrix:
| Predicted Risk | Production Outcome | Attribution Classification | Operational Meaning |
|---|---|---|---|
| `LOW` / `MEDIUM` | `SUCCESS` | **`ACCURATE_LOW_RISK`** | Correctly identified safe change. |
| `HIGH` / `CRITICAL`| `FAILURE` / `INCIDENT` / `ROLLED_BACK` | **`ACCURATE_HIGH_RISK`** | Correctly predicted hazardous change. |
| `LOW` / `MEDIUM` | `FAILURE` / `INCIDENT` / `ROLLED_BACK` | **`UNDERESTIMATED_RISK`** | Risky blindspot / false negative. |
| `HIGH` / `CRITICAL`| `SUCCESS` | **`OVERESTIMATED_RISK`** | Conservative false alarm / false positive. |
| N/A | Any | **`UNATTRIBUTED`** | No prior analysis run found for commit. |

---

## 3. Strict Safety & Governance Principles

1. **Zero Causal Inference**: The platform reports factual, observational correlations. It does not fabricate causal claims.
2. **Zero Retroactive Mutation**: Historical `RiskAssessment` and `DecisionRecord` records are immutable audit logs and are **never** altered.
3. **No Autonomous Weight Drift**: Risk scoring rules remain deterministic and version-controlled. Telemetry informs human engineering teams rather than silently changing scoring algorithms at runtime.