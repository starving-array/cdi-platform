# Domain Events (V1 MVP)

This document defines the events that drive the change analysis lifecycle.

## 1. Event vs. Command Distinction
- **Command**: A request to perform an action. Imperative intent. It can fail or be rejected. Example: `AnalyzeChange`, `InvestigateRisk`.
- **Event**: A factual record that something has already happened in the past. It cannot be rejected or undone. Example: `RiskAssessed`, `DecisionGenerated`.

## 2. Internal vs. External Events
For V1, **ALL** defined events are **Internal Application/Domain Events**.
They are published and consumed entirely within the modular monolith (e.g., via Spring ApplicationEvents or an internal transactional outbox).
We strictly **DO NOT** introduce Kafka or external event brokers. The payload structures are designed to allow seamless migration to an external event bus in the future if required.

## 3. Event Reliability and Idempotency
- **Event Ordering**: Event consumers cannot guarantee strict ordering (e.g., processing `RiskAssessed` before a newer `ChangeProposed` arrives).
- **Idempotency**: Consumers must be idempotent. They must track the `CausationId` or `CommitSHA` to ensure they don't process an obsolete event.
- **Transactions**: Events should ideally be published via a Transactional Outbox pattern to ensure they are not lost if the database commits but the application crashes.

---

## 4. Defined Domain Events

### 4.1 ChangeProposed
- **Purpose**: Signals that a new change has entered the system and is ready for analysis.
- **Producer**: `ReceiveChange` Use Case.
- **Consumer**: `AnalyzeChange` Background Worker.
- **Key Payload**:
  - `ChangeId`, `TenantId`, `RepositoryURI`, `PullRequestId`, `CommitSHA`.

### 4.2 RiskAssessed
- **Purpose**: Signals that the deterministic risk calculation has finished. Used to trigger conditional downstream logic (like AI agents).
- **Producer**: `AnalyzeChange` Use Case.
- **Consumer**: Agent Orchestrator (decides if `InvestigateRisk` should be enqueued) and Policy Evaluator.
- **Key Payload**:
  - `RiskAssessmentId`, `ChangeId`, `RiskScore`.

### 4.3 InvestigationCompleted
- **Purpose**: Signals that the AI Agent has finished its reasoning loop and saved structured findings.
- **Producer**: `InvestigateRisk` Use Case.
- **Consumer**: Policy Evaluator (triggers `GenerateDecision`).
- **Key Payload**:
  - `InvestigationId`, `ChangeId`, `Status` (SUCCESS/FAILED).

### 4.4 DecisionGenerated
- **Purpose**: Signals that a final deterministic decision has been reached. Used to publish statuses back to source control and update dashboards.
- **Producer**: `GenerateDecision` Use Case.
- **Consumer**: Status Publisher (calls `SourceControlPort`), Audit Logger.
- **Key Payload**:
  - `DecisionId`, `ChangeId`, `CommitSHA`, `Outcome` (APPROVE, REVIEW_REQUIRED, BLOCK), `PolicyVersionId`.

### 4.5 EvidenceIngested — NOT defined yet (deferred)
`EvidenceIngested` is mentioned only in `bounded-contexts.md` §1 and is deliberately
**absent** from this canonical catalog. The P2 SearchEvidence delivery (ADR-008) is a
read-only query over the deterministic `evidence_record` store and publishes no events;
evidence *ingestion* is explicitly out of scope for it. The event will be defined here
only when an ingestion use case exists (per the "publish only what has a consumer or a
stated audit need" rule, application-layer.md §12).

### 4.6 DecisionOverridden
- **Purpose**: Signals that a `TENANT_ADMIN` has recorded the single, immutable
  human override of a decision's outcome (UC-06 OverrideDecision, ADR-006). Used
  for audit logging of who changed what outcome and why.
- **Producer**: `OverrideDecision` Use Case.
- **Consumer**: Audit Logger.
- **Key Payload**:
  - `AnalysisRunId`, `ChangeId`, `DecisionId`, `OriginalOutcome`, `Outcome`
    (the post-override effective outcome, `APPROVE`/`REVIEW_REQUIRED`/`BLOCK`),
    `CommitSHA`, plus the standard envelope fields. The actor and justification
    live on the persisted `human_override` row (data-model.md §E), not on the
    event.
- **Standards**: published in the same transaction as the saved override
  (application-layer.md §8) through `DomainEventPublisher`; the original
  `decision_record` row is never modified by this event's producer.

---

## 5. Rejected Events
- **ChangeAnalysisStarted / EvidenceCollected**: Rejected. These are too granular. Emitting events for every sub-step of the `AnalyzeChange` command creates an unnecessary, chatty event-driven architecture that is hard to debug.
- **InvestigationRequested**: Rejected. This is a Command (`InvestigateRisk`), not an Event.

---

## 6. Standard Event Envelope

Every event must wrap its payload in a standard envelope:

```json
{
  "eventId": "uuid-1234",
  "eventType": "DecisionGenerated",
  "schemaVersion": "1.0",
  "timestamp": "2026-08-11T08:00:00Z",
  "correlationId": "analysis-run-uuid-5678",
  "causationId": "command-uuid-9012",
  "aggregateId": "change-uuid-3456",
  "aggregateType": "Change",
  "payload": {
    "outcome": "APPROVE",
    "commitSha": "abc123def456"
  }
}
```

- **CorrelationId**: Unifies all events belonging to a single analysis run across the system.
- **CausationId**: Identifies the specific command or upstream event that caused this event to be emitted.
