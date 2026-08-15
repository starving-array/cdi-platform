# Risk Engine Domain

This document describes the Risk domain implementation for the V1 Engineering Change Decision Intelligence platform.

## 1. Risk vs Policy vs Decision
The Risk context is completely separated from the Decision context. 
- **Risk** purely asks: "How risky is this change based on deterministic engineering signals?" 
- **Policy** contains the organizational rules ("If risk is HIGH and Tier 0, require review").
- **Decision** combines these to form a final outcome (APPROVE/BLOCK).
The Risk Engine NEVER approves, blocks, or dictates policy. It only assesses risk.

## 2. Risk Score Semantics
The Risk Score is a deterministic index bounded between 0 and 100. It is NOT a statistical probability (e.g., it does not mean an 80% chance of failure). It is simply an easily understandable, quantifiable magnitude of danger based on predefined rules.

## 3. Risk Levels
The score maps deterministically to a risk level to support easier policy rule authoring:
- **0-39:** LOW
- **40-59:** MEDIUM
- **60-79:** HIGH
- **80-100:** CRITICAL

## 4. Risk Factors
A `RiskFactor` provides explainability. It explicitly identifies what increased the risk score, the amount of the contribution, and an explanation.
Supported factors in V1:
- `HIGH_CHANGE_SIZE`
- `HIGH_SERVICE_CRITICALITY`
- `HIGH_DEPENDENCY_IMPACT`
- `DATABASE_SCHEMA_CHANGE`
- `CONFIGURATION_CHANGE`
- `HISTORICAL_INCIDENT_MATCH`

## 5. Scoring Formula
The Risk Engine applies a transparent additive model:
- `Base Score`: 10
- `Tier 0 Service`: +30
- `Tier 1 Service`: +15
- `Database Migration`: +25
- `Historical Incident Match`: +20
- `High Dependency Impact (>5)`: +15
- `Configuration Change`: +10
- `High Change Size (>20 files)`: +10

The final score is capped at 100.

## 6. Risk Rule Versioning
To maintain auditability, the Risk Engine stamps the rule set version (e.g., `v1.0.0`) on every assessment. This guarantees that we know exactly which calculation logic produced a historical score.

## 7. Evidence References
`RiskFactor` objects maintain traceability by storing an array of `EvidenceId`s when they are derived from evidence (such as historical incidents). This prevents duplicating evidence blobs into the risk context.

## 8. Degraded Evidence Behavior
The `RiskAssessmentInput` and `RiskAssessment` track an `EvidenceState` (`EVIDENCE_AVAILABLE`, `NO_RELEVANT_EVIDENCE`, `EVIDENCE_RETRIEVAL_FAILED`). The risk calculation itself does NOT automatically penalize the change for a failure to retrieve evidence, keeping the score deterministic based strictly on code structure and retrieved facts. A policy in the Decision context can independently block the PR if the evidence retrieval failed (fail-safe).

## 9. Determinism
The `DeterministicRiskEngine` is mathematically pure. Given the same inputs, it produces the exact same score, risk level, and factors. It performs zero IO operations, accesses no databases, and calls no APIs.

## 10. Future ML Extension Point
In the future, the deterministic score can be replaced or augmented with a calibrated ML probability. If this happens, the semantics of `RiskScore` will shift from an additive index to a predictive probability, but the boundary between Risk and Policy will remain untouched.
