# Policy & Decision Domain

This document specifies the Policy and Decision domains for the V1 Engineering Change Decision Intelligence platform.

## 1. Risk vs Policy vs Decision
- **Risk Assessment**: Objective measurement of danger (e.g., HIGH risk).
- **Policy**: Deterministic rules dictating organizational boundaries (e.g., "Tier 0 + HIGH risk = BLOCK").
- **Decision**: The final outcome linking the specific risk, exact policy version, and resulting required actions.

## 2. Policy Aggregate
The `Policy` aggregate root represents the current rule set for a `Tenant`. It contains `PolicyRule` objects and tracks the `PolicyVersion` and `PolicyStatus` (ACTIVE/ARCHIVED). 

## 3. Policy Versioning
Policies are strictly versioned. Modifying a policy logically creates a new `PolicyVersion`. A `DecisionRecord` preserves the exact `PolicyId` and `PolicyVersion` used at evaluation time. If the tenant updates their policy, historical `DecisionRecord`s remain unchanged and auditable.

## 4. Policy Rules
V1 utilizes deterministic `PolicyRule` objects, avoiding dynamic expressions (no CEL/OPA yet) to guarantee predictability. A rule specifies its target inputs (`targetTiers`, `targetRiskLevels`, `requiredEvidenceStates`) and maps them to a `DecisionOutcome` and a list of `RequiredAction`s.

## 5. Policy Evaluation
The `PolicyEngine` evaluates the inputs against all rules in the `Policy`. It iterates through the rules, finds all matches based on objective facts, and derives the outcome.

## 6. Rule Precedence
If multiple rules match, they are resolved deterministically using a hardcoded precedence:
`BLOCK` > `REVIEW_REQUIRED` > `APPROVE`
If no rules match, the system fails-safe to `REVIEW_REQUIRED`.

## 7. Decision Outcomes
- `APPROVE`: Proceed automatically.
- `REVIEW_REQUIRED`: Proceed only after required reviews are completed.
- `BLOCK`: Do not proceed under current policy.

## 8. Decision Reasons
The `DecisionRecord` contains `DecisionReason` objects which explain *why* the outcome was reached, tracing back to the explicit `ruleId` and any applicable `EvidenceId` references.

## 9. Required Actions
The system captures `RequiredAction`s (e.g., `HUMAN_REVIEW`, `SECURITY_REVIEW`). These represent states the external workflow layer must enforce. The decision domain simply records that they are required, without implementing a full workflow engine.

## 10. Human Overrides
A `HumanOverride` value object allows a human to bypass the system's decision (e.g., unblocking a critical hotfix). Overrides are applied immutably via `decision.withOverride()`, which creates a new instance while preserving the `originalOutcome` alongside the override actor and justification.

## 11. AI Findings Boundary
Currently, the LLM/Investigation context is excluded. When introduced, the AI will produce structured outputs (findings/classifications) which the application layer will validate and convert into deterministic flags/enums. Only these validated, strongly-typed inputs will be fed into the `PolicyEngine`. Raw LLM string outputs will never dictate policy branching.

## 12. Historical Immutability
`DecisionRecord` objects have no setters. Once evaluated, they represent a permanent historical artifact.
