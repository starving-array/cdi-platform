# Application Layer Design (V1)

**Status**: Design only — no code is implemented from this document yet.
**Applies to**: `com.cdi.application` package (planned).

This document is the design contract for the Application layer of the V1 Change Decision Intelligence platform. It orchestrates the existing domain model (`src/main/java/com/cdi/*/domain`) and the abstract ports, and must be consistent with `docs/02-architecture/use-cases.md`, `docs/02-architecture/analysis-workflow.md`, `docs/02-architecture/ports-and-adapters.md`, `docs/02-architecture/domain-model.md`, `docs/02-architecture/bounded-contexts.md`, `docs/04-api/api-contract.md`, `docs/04-api/domain-events.md`, and ADR-004 through ADR-007.

**Hard boundary for V1**: The Application layer contains **no** SQL/JPA repository implementations, **no** HTTP/web controllers, **no** provider SDKs (GitHub/LLM/vector search), **no** queue/persistence implementations, and **no** Flyway migrations. It contains only orchestration logic, port interfaces, and error/result contracts. The domain layer (`com.cdi.*.domain`) must not be modified by the Application layer design.

---

## 1. Responsibilities of the Application Layer

1. **Orchestrate use cases**: Instantiate domain aggregates, call domain methods, and coordinate ports. Application classes never implement domain rules — domain rules stay in the domain model.
2. **Enforce Command/Query Separation (CQS)**: State-mutating operations (Commands) are structurally separate from read operations (Queries). Logical separation only — no separate read models, no separate databases, no CQRS infrastructure (KISS).
3. **Own transaction boundaries**: Open short, DB-local transactions for state changes; **never** hold a transaction open during an external call (SCM API, LLM, vector search). See Section 8.
4. **Translate port/infrastructure failures** into the canonical `ApplicationError` contract (Section 9) so the API layer can map them to HTTP responses.
5. **Validate application-level structure**: e.g., structural validation of `AgentPort` outputs (citations reference real `EvidenceRecord` IDs) before persisting (per `ports-and-adapters.md` §2.4).
6. **Enforce role authorization**: Actors are resolved upstream (auth). The Application layer checks role per use case: `SYSTEM_WORKER`, `TENANT_ADMIN`, `ENGINEER`.
7. **Own idempotency keys**: The Application layer computes, checks, and stores idempotency keys (Section 10). Domain and persistence layers stay agnostic.
8. **Emits internal events** through a domain-event publisher backed by the transactional outbox (Section 12). Events are internal-only for V1 (no Kafka).
9. **Coordinate background work** through `JobQueuePort` only — the Application layer never touches queue internals (`ADR-004`: modular monolith, no broker).
10. **Remains deterministic where the domain is deterministic**: `DeterministicRiskEngine` and `PolicyEngine` are called directly; they are domain services, not ports (`ports-and-adapters.md` §3 rejects `RiskAssessmentPort` and `PolicyPort`).

Non-responsibilities (explicitly out of scope): REST mapping, DTO–HTTP translation, persistence mapping, provider authentication, CDC, and schema management.

---

## 2. Package Structure

The Application layer mirrors the existing bounded contexts at `com.cdi.<context>.domain`. Each command/query pair lives in its context package. Ports are shared and live under `common.port`.

```
com.cdi.application
├── common
│   ├── ApplicationError.java            # canonical error catalog (Section 9)
│   ├── ApplicationException.java        # carries ONE ApplicationError + context
│   ├── IdempotencyKey.java              # value object wrapping the computed key
│   ├── Actor.java                       # resolved actor: id + Role (SYSTEM_WORKER | TENANT_ADMIN | ENGINEER)
│   ├── port
│   │   ├── SourceControlPort.java       # external (see Section 7)
│   │   ├── SystemContextPort.java       # external
│   │   ├── EvidenceSearchPort.java      # external
│   │   ├── AgentPort.java               # external, defined now, first used in P1
│   │   └── JobQueuePort.java            # internal (same-DB)
│   ├── event
│   │   └── DomainEventPublisher.java    # transactional-outbox contract (Section 12)
│   └── result
│       ├── CommandResult.java           # (one of) IdempotentCommandResult / EmptyCommandResult
│       └── IdempotentCommandResult.java # runId + created flag
│
├── change
│   ├── ProposeChangeCommand.java        # record (input)
│   ├── ProposeChangeHandler.java        # Command  (sync, P0)
│   ├── GetChangeQuery.java             # record (input)
│   ├── GetChangeQueryService.java      # Query     (sync, P0)
│   └── ChangeAnalysisView.java          # read result: Change + AnalysisRun + Risk + Decision
│
├── analysis
│   ├── RequestAnalysisCommand.java
│   ├── RequestAnalysisHandler.java      # Command (sync, P0)
│   ├── AnalyzeChangeCommand.java        # worker payload (async, P0)
│   ├── AnalyzeChangeHandler.java        # async orchestration (P0)
│   ├── GetAnalysisRunQuery.java
│   ├── GetAnalysisRunQueryService.java  # Query (sync, P0)
│   ├── ListAnalysisRunsQuery.java            # (P1)
│   ├── ListAnalysisRunsQueryService.java     # (P1)
│   └── AnalysisRunView.java
│
├── decision
│   ├── GenerateDecisionCommand.java     # worker payload (async, P0)
│   ├── GenerateDecisionHandler.java     # P0
│   ├── OverrideDecisionCommand.java          # (P1)
│   ├── OverrideDecisionHandler.java         # (P1)
│   └── GetDecisionByRunQueryService.java    # folded into GetAnalysisRun; see note
│
├── organization
│   ├── CreateOrganizationCommand.java        # (P1)
│   ├── CreateOrganizationHandler.java       # (P1)
│   ├── GetOrganizationQuery.java
│   └── GetOrganizationQueryService.java  # P0 (support query)
│
├── repository
│   ├── CreateRepositoryCommand.java          # (P1)
│   ├── CreateRepositoryHandler.java         # (P1)
│   ├── GetRepositoryQuery.java
│   └── GetRepositoryQueryService.java    # P0 (support query)
│
├── systemcontext
│   ├── CreateServiceCommand.java             # (P1)
│   ├── CreateServiceHandler.java            # (P1)
│   ├── GetServiceQuery.java
│   └── GetServiceQueryService.java       # P0 (support query)
│
├── policy
│   ├── CreatePolicyCommand.java              # (P1)
│   ├── CreatePolicyHandler.java             # (P1)
│   ├── GetPolicyQuery.java
│   ├── GetPolicyQueryService.java        # P0 (support query)
│   ├── ListPolicyVersionsQuery.java          # (P1)
│   └── ListPolicyVersionsQueryService.java   # (P1)
│
└── evidence
    └── SearchEvidenceQueryService.java   # (P2, first P2 capability — ADR-008)
        # Query service + SearchEvidenceResult view for use-cases.md §6.2.
        # P0/P1 used EvidenceSearchPort directly; P1 adds:
        # validateInvestigationFindings(...) helpers invoked by InvestigateRiskHandler.
```

**Naming conventions**
- Command inputs are immutable `record`s named `<UseCase>Command`.
- Command orchestrators are classes named `<UseCase>Handler`; they implement exactly one use case.
- Query inputs are `record`s named `<UseCase>Query`; query services are `<UseCase>QueryService`.
- Worker commands share the same shape but are consumed from the queue via `JobQueuePort`; they are still Application-layer classes (the queue adapter only deserializes and invokes them).
- Ports are Java **interfaces** in `common.port`. Adapters implement them in `com.cdi.*.adapter` packages (not part of this design).
- Application classes may use framework-level annotations for wiring and transaction demarcation only (e.g., Spring `@Service`, `@Transactional`). They must **not** import any provider/client SDK, persistence API, or HTTP API.

**Dependency rule**: `com.cdi.application.*` may depend on `com.cdi.*.domain`, `com.cdi.common.domain.*`, and `com.cdi.application.common.*` only. No package may depend on an adapter package. Domain packages must not depend on `com.cdi.application.*`.

**Empty-package rule**: Packages exist only if they contain classes. `evidence` in the tree above is illustrative only — do not create it until P1.

---

## 3. Use-Case Inventory

Canonical V1 use cases (commands mutate, queries read). Actors: **SW** = System Worker, **TA** = Tenant Admin, **EN** = Engineer.

| # | Use case | Kind | Actor | Execution | Tier |
|---|----------|------|-------|-----------|------|
| UC-01 | ProposeChange | Command | EN / System (webhook) | Sync | **P0** |
| UC-02 | RequestAnalysis | Command | EN | Sync | **P0** |
| UC-03 | AnalyzeChange | Command (worker) | SW | Async | **P0** |
| UC-04 | GenerateDecision | Command (worker) | SW | Async | **P0** |
| UC-05 | InvestigateRisk | Command (worker) | SW | Async | **P1** |
| UC-06 | OverrideDecision | Command | TA | Sync | **P1** |
| UC-07 | CreateOrganization | Command | TA | Sync | **P1** |
| UC-08 | CreateRepository | Command | TA | Sync | **P1** |
| UC-09 | CreateService | Command | TA | Sync | **P1** |
| UC-10 | CreatePolicy | Command | TA | Sync | **P1** |
| UC-11 | GetChange | Query | EN | Sync | **P0** |
| UC-12 | GetAnalysisRun | Query | EN | Sync | **P0** |
| UC-13 | GetOrganization | Query | EN/TA | Sync | **P0** |
| UC-14 | GetRepository | Query | EN/TA | Sync | **P0** |
| UC-15 | GetService | Query | EN/TA | Sync | **P0** |
| UC-16 | GetPolicy | Query | EN/TA | Sync | **P0** |
| UC-17 | ListAnalysisRuns | Query | EN | Sync | **P1** |
| UC-18 | ListPolicyVersions | Query | TA | Sync | **P1** |

Post-V1 items. The V1 inventory ends at UC-18. **SearchEvidence is the first P2
capability** (`use-cases.md` §6.2) and is delivered as a deterministic,
query-first evidence search (ADR-008) — it is **not** a V1 UC and **not**
"UC-19". Still deferred entirely (Product backlog / P2): ListChanges list/feed
UI, SuspendOrganization, ArchiveRepository, DeprecateService, outcome/deployment
tracking, ML attribution. The internal `EvidenceRecord` search surface was
already exposed during P1 via `InvestigateRisk`; SearchEvidence now makes it a
public (ENGINEER-only) query.

---

## 4. P0 / P1 / P2 Classification

### P0 — must exist to complete the core value loop
- **UC-01 ProposeChange**: intakes the change and enqueues analysis (`analysis-workflow.md` §1.1). Without it, nothing enters the system.
- **UC-03 AnalyzeChange**: the deterministic orchestrator (normalize → context → evidence → risk). Produces the `RiskAssessment` that everything downstream depends on.
- **UC-04 GenerateDecision**: evaluates `PolicyEngine` against `RiskAssessment` and publishes a `DecisionRecord`. This is the product's actual decision output.
- **UC-02 RequestAnalysis**: manual re-trigger for Engineers (`use-cases.md` §5.3 actor). Required to test and demo re-analysis.
- **UC-11 GetChange, UC-12 GetAnalysisRun**: read back the analysis state for the UI/CLI and for the SCM status payload.
- **UC-13..UC-16 (support queries)**: cheap read shortcuts required by the P0 slice (repository → provider metadata, service → criticality, policy → tenant rules). They are trivial and reduce P0 test friction; no admin writes needed.

### P1 — required for a complete V1 but after the vertical slice
- UC-05 InvestigateRisk (agent path — `analysis-workflow.md` §1.6): conditional on HIGH risk / TIER_0 / conflicting evidence (default **off**; P0 ships deterministic-only and pipelines skip this stage).
- UC-06 OverrideDecision (human control per ADR-006): mutates `DecisionRecord` with a mandatory `HumanOverride`.
- UC-07..UC-10 Create* admin flows (self-service tenant setup; PowerShell/bootstrapping can create the first records instead).
- UC-17 ListAnalysisRuns, UC-18 ListPolicyVersions (list/versioning UI).

### P2 — explicitly deferred
- **SearchEvidence (query-first, deterministic — delivered first, ADR-008)**: the
  semantic (pgvector) side stays deferred; the search UI is a separate P2/backlog item.
- Semantic (pgvector) evidence retrieval, agent-discovered evidence, search UI, outcome
  attribution, deployment integration, cross-tenant dashboards.

**Rationale for deferring the agent to P1**: ADR-005 makes risk and policy enforcement deterministic; the agent is a conditional enhancement whose own failure mode is graceful degradation to the deterministic path (`ports-and-adapters.md` §4, `analysis-workflow.md` §7). Therefore P0 delivers the full *value* of the system (change in → traceable decision out) with zero LLM cost and no dependency on an external provider. The `AgentPort` interface is still declared in P0 (Section 7) so the shape is fixed, but it has no P0 caller.

---

## 5. P0 Vertical Slice

A single, end-to-end, non-CRUD horizontal slice that touches the real domain behavior:

```
        ProposeChange ──(sync)──▶ Change (OPEN) + AnalysisRun (QUEUED)
            │
            ▼                           (async worker, via JobQueuePort)
        AnalyzeChange ──▶ SourceControlPort.getDiff            (external, no tx)
                            SystemContextPort.getServiceCriticality (external, no tx)
                            EvidenceSearchPort.searchSimilarChanges  (external, no tx)
                            DeterministicRiskEngine.assess(...)      (pure domain)
                            ▲ persist RiskAssessment; run QUEUED→RUNNING→COMPLETED (short tx)
            │
            ▼                           (async worker, via JobQueuePort)
        GenerateDecision ──▶ PolicyEngine.evaluate(policy, risk, tier, now)   (pure domain)
                            ▲ persist DecisionRecord (short tx)
                            ▲ publish status via SourceControlPort AFTER commit
```

**Decisions the slice proves** (each maps to a real domain unit test already in the codebase):

1. **Committed snapshot is locked**: `AnalysisRun` binds a `CodeSnapshot(commitSha, branch)` at intake (`Change.updateLatestCommit` guarded to OPEN, `AnalysisRun.start()` guarded to QUEUED).
2. **Risk is deterministic and separated**: `DeterministicRiskEngine.assess(runId, RiskAssessmentInput, calculatedAt)` — produces an immutable `RiskAssessment` with factors, score, level, and `EvidenceState`.
3. **Risk never decides**: `RiskAssessment` contains **no** policy actions; it is fed into `PolicyEngine.evaluate(...)`.
4. **Decision is a traceable snapshot**: `DecisionRecord` references `tenantId`, `analysisRunId`, `riskAssessmentId`, `policyId`, `policyVersion`, reasons, and required actions — a fully reproducible verdict.
5. **Safety default**: with no matching policy rule, `PolicyEngine` returns `REVIEW_REQUIRED` (never approve-by-ommission).
6. **Timeboxed external work**: all three external port calls happen outside any transaction (Section 8).

**Slice boundary (in-scope domain objects)**: `Change`, `AnalysisRun`, `CodeSnapshot`, `EvidenceState`, `CriticalityTier`, `EvidenceId`, `RiskAssessmentInput`, `RiskAssessment`, `DeterministicRiskEngine`, `Policy`, `PolicyRule`, `PolicyEngine`, `DecisionRecord`, `DecisionReason`, `DecisionOutcome`, `RequiredAction`, `AnalysisFailure`.

**Out-of-scope for the slice** (P1+): `HumanOverride`, agent findings/citations, semantic search, evidence discovery by agent. `InvestigateRisk` is *not* part of P0; the worker treats "HIGH risk" as "decision is now REVIEW_REQUIRED-or-above by default policy" until P1 adds the agent.

**`FileDiff` — determination A: required domain concept** (resolution of the previous "design assumption" note):
- **Evidence requiring it**: `domain-model.md` §B lists `FileDiff` (VO: path, additions, deletions, change type); `analysis-workflow.md` §1.2 makes normalization *generate* `FileDiff` objects and §1.3 feeds them into context collection; `data-model.md` §B persists them as `changed_file(tenant_id, analysis_run_id, path, additions, deletions)`; `ports-and-adapters.md` §2.1 has `getDiff(...)` return a `FileDiff` list. It is a real, cross-cutting snapshot artifact — **not** an obsolete or docs-only reference.
- **Minimal responsibility**: an immutable value object describing one changed file in a snapshot — `(path, additions, deletions, changeType)` where `changeType ∈ {ADDED, MODIFIED, DELETED, ...}`. Produced by `SourceControlPort.getDiff` during the **normalization** stage; persisted per `AnalysisRun` (`changed_file`); aggregated into `RiskAssessmentInput` (changedFilesCount / linesAdded / linesDeleted) and consumed by `EvidenceSearchPort` (file paths).
- **Placement nuance**: `domain-model.md` lists it under the **Change context**, but the implemented `Change` aggregate deliberately tracks only `latestCommitSha` and holds no diff list. The concrete type therefore belongs with the *snapshot* side of an `AnalysisRun` (`com.cdi.change.domain.FileDiff` or the analysis context at implementation time), mirroring `analysis_run → changed_file`. It must **not** be attached to the `Change` aggregate (that would violate the implemented aggregate boundary).
- **Not implemented in this task**: the Application workflows are not being built, and `SourceControlPort.getDiff` is the only producer, so `FileDiff` is a P0 implementation prerequisite, flagged — not created now.

### 5.1 AnalyzeChange Orchestration Boundary (anti-god-service guard)

`AnalyzeChangeHandler` is the highest-risk application component for becoming a "god service" (SCM logic + evidence + risk math + policy + AI + persistence in one class). The boundary that the future implementation must respect:

| Responsibility | Owner | Must NOT do |
|---|---|---|
| Sequencing, step ordering, run state transitions (claim / complete / fail), assembling `RiskAssessmentInput` from step outputs, deciding to enqueue `GenerateDecision`, translating failures into `ApplicationError`/`AnalysisFailure` | **Application** (`AnalyzeChangeHandler` + small package-private collaborators) | Compute risk numbers, know GitHub/SDK details, write SQL/queries, run LLM reasoning |
| Scoring formula, thresholds, factor generation, `EvidenceState` semantics | **Domain** — `DeterministicRiskEngine` (pure), `risk-engine.md` | Call ports, do IO, approve/block |
| Rule precedence (`BLOCK > REVIEW_REQUIRED > APPROVE`), fail-safe fallback, risk↔decision separation | **Domain** — `PolicyEngine`, `policy-decision-domain.md` | Approve autonomously |
| Run/Change state machines and invariants | **Domain** — `AnalysisRun`, `Change` | None (guarded transitions only) |
| Diff fetch/normalization, context/criticality, evidence retrieval, job enqueue | **Ports** — `SourceControlPort`, `SystemContextPort`, `EvidenceSearchPort`, `JobQueuePort` | Expose provider types to application |
| LLM reasoning | **Port** — `AgentPort` (P1) | Dictate policy |

Concrete rules for the future implementation (KISS/SOLID, per `ENGINEERING_CONSTITUTION`):
1. The handler calls **domain services** for every formula and **port interfaces** for every external interaction; it never re-implements either.
2. Natural seams are extracted as small package-private application collaborators **only when a method exceeds its responsibility** (e.g., `SnapshotNormalizer`, `RiskInputAssembler`). No speculative classes (YAGNI).
3. The handler moves plain domain objects between steps — never provider SDK types, never "context bags" of raw port results.
4. Target: `AnalyzeChangeHandler` remains a thin sequence (≤ ~80 lines); any port call is a one-liner; retries/degradation come from the port failure contract (§9.2), not inline in the handler.
5. Persistence is reached only through repository interfaces owned by adapters; the handler never touches JPA/SQL.

---

## 6. Command/Query Model (CQS)

Commands and queries are structurally separated. Every command/query defines: input record, orchestrator class, domain objects touched, ports used, transaction scope, output, and failure mapping.

### Commands (P0)

**UC-01 ProposeChangeCommand** — sync
- Input: `tenantId, repositoryId, providerChangeId, commitSha, branch, title, description, author, actor, idempotencyKey`
- Logic: recompute/verify `IdempotencyKey`; if an existing change with same natural key (`tenantId, repositoryId, providerChangeId`) has `latestCommitSha == commitSha`, return its latest run (**duplicate → same run, `created=false`**). Else build `Change(...)` + `CodeSnapshot(commitSha, branch)` + `AnalysisRun(...)`; `DomainEventPublisher.publish(ChangeProposed)`;
- Ports: `JobQueuePort.enqueue(AnalyzeChangeCommand, key)` (same-DB)
- Tx: one short tx (insert `Change` + `AnalysisRun` + outbox + job row)
- Output: `IdempotentCommandResult(analysisRunId, created)`
- Failure: persistence/queue down → webhook 500 (`ports-and-adapters.md` §4, JobQueuePort row).

**UC-03 AnalyzeChangeCommand** — async worker
- Input: `analysisRunId`
- Logic: claim run (`QUEUED→RUNNING`, CAS, see §11). If claim lost → no-op (idempotent replay). Fetch diff (`SourceControlPort`), resolve criticality (`SystemContextPort`), fetch evidence (`EvidenceSearchPort`), build `RiskAssessmentInput(...)`, `DeterministicRiskEngine.assess(...)`, persist `RiskAssessment`, run → `RUNNING→COMPLETED`, enqueue `GenerateDecisionCommand`.
- Ports: SourceControl, SystemContext, EvidenceSearch (external, **outside tx**); JobQueue (inside final tx).
- Tx: **no global tx**; per-step small txs (`use-cases.md` §5.2). Final step (persist risk + mark run complete + enqueue) is one small tx.
- Output: void (state changes only).
- Failure: per-port fallback table (§9); terminal failure records `AnalysisFailure(category, failureCode, failedAt)` via `AnalysisRun.fail(...)` (run `FAILED`).

**UC-04 GenerateDecisionCommand** — async worker
- Input: `analysisRunId`
- Logic: load latest `RiskAssessment` + tenant `Policy`; `PolicyEngine.evaluate(policy, riskAssessment, tier, generatedAt)`; persist `DecisionRecord`; `DecisionGenerated` event via outbox (this triggers the status check side effect after commit).
- Ports: `SourceControlPort.publishStatusCheck` **after** commit (see Section 12).
- Tx: one short tx (persist `DecisionRecord` + outbox event).
- Output: void.
- Failure: policy load/eval problem → `POLICY_EVALUATION_FAILED` → run `FAILED` with `AnalysisFailure`.

**UC-02 RequestAnalysisCommand** — sync (Engineer manual re-analysis, or internal trigger)
- Input: `changeId, commitSha, actor, idempotencyKey` (`commitSha` is the commit snapshot to analyze; `api-contract.md` §3.3 `POST /changes/{changeId}/analyze` sends `{"commitSha": ...}` with `Idempotency-Key: <commitSha>`).
- **Idempotency (corrected design)**: RequestAnalysis is **idempotent** for the same logical analysis request — i.e., the same `(tenant, change, commit)` — and MUST NOT create duplicate `AnalysisRun`s. Key = `IdempotencyKey.analyze(tenantId, changeId, commitSha)`: a tenant-scoped key whose user-supplied shard is `commitSha` (matches the API header), with `tenantId` + `changeId` guaranteeing cross-tenant and cross-PR isolation. The key is recorded on the existing `analysis_job` row (`data-model.md` §F), **no new persistence table**.
- **Resolution rule**: `AnalysisRun` is unique per `(tenant_id, change_id, commit_sha)` (`data-model.md` §5). The handler always resolves by that triple; state determines the behavior (table below). It never inserts a second run for the same snapshot.
- **State behavior** (for the existing run on the requested snapshot):

  | Existing run status | Behavior |
  |---|---|
  | `QUEUED` | Return the existing run (idempotent success). No new run, no enqueue. |
  | `RUNNING` | Return the existing run (idempotent success, 200 per `api-contract.md` §3.3). No new run, no enqueue. |
  | `COMPLETED` | Return the existing run; the analysis for that snapshot is already final and is the idempotent answer. A forced re-evaluation of the *identical* snapshot is a P1 concern and must be an explicit, separate operation — never a silent duplicate. |
  | `FAILED` | Return the existing run and **re-enqueue** `AnalyzeChange` for that same run (retry) via the domain `AnalysisRun.retry()` (FAILED→QUEUED). Guarded by `analysis_job.UNIQUE(tenant_id, idempotency_key)` (`data-model.md` §5), so concurrent retries collapse to one job; the worker claims via `QUEUED→RUNNING` CAS (§11). |
  | `SUPERSEDED` | Return the existing run as the historical fact (idempotent success, `status=SUPERSEDED`). Terminal — cannot be re-run; no enqueue. |

- **Cross-commit rule**: `commitSha` normally equals `change.latestCommitSha` (analysis is bound to the change's current snapshot — `domain-model.md` §B invariant). If a historical run exists for an older `commitSha`, return it idempotently (it is the record of that snapshot). If `commitSha` differs from `latestCommitSha` and **no** run exists for it, reject with `ANALYSIS_SUPERSEDED` (409) — the change is governed by a newer snapshot.
- **Closed/merged change**: reject (`CHANGE_NOT_FOUND`-family / domain violation).
- Tx: one short tx (read-only resolution; job re-activation only in the FAILED retry case).
- Output: `IdempotentCommandResult(analysisRunId, created)` (`created=false` on any idempotent reuse).
- **Separation of concerns**: idempotency (this UC, application layer) ≠ concurrency (worker CAS claim, §11) ≠ final safety net (DB `UNIQUE (tenant_id, change_id, commit_sha)`, `data-model.md` §5). All three are required and independent.

**UC-06 OverrideDecisionCommand** — sync (P1, ADR-006)
- Input: `tenantId, actor, analysisRunId, decisionId, newOutcome, justification` (all required; actor must be `TENANT_ADMIN` — `UNAUTHORIZED` otherwise)
- Logic: resolve the tenant-scoped run (`ANALYSIS_RUN_NOT_FOUND` for missing/cross-tenant; `ANALYSIS_SUPERSEDED` for a superseded run); resolve the decision for that run/tenant and verify it matches `decisionId` (`DECISION_NOT_FOUND`); reject already-overridden (`DECISION_ALREADY_OVERRIDDEN`). Apply `HumanOverride(actor.id, decision.originalOutcome, newOutcome, justification, clock.instant())` via the immutable `DecisionRecord.withOverride(...)` — never mutates the original row — and persist; publish `DecisionOverridden` (outbox).
- Ports: `AnalysisRunRepository`, `DecisionRecordRepository`, `DomainEventPublisher` (all internal / same-DB)
- Tx: one short tx (persist aggregate incl. 1:1 `human_override` child + outbox event)
- Output: `OverrideDecisionResult(decisionId, originalOutcome, outcome)`
- **Non-idempotent (frozen D3/§10)**: carries **no** `IdempotencyKey` and is never replayed; a repeat resolves to `DECISION_ALREADY_OVERRIDDEN`. One shot only.
- Failure: the two decision-specific additions `DECISION_NOT_FOUND` / `DECISION_ALREADY_OVERRIDDEN` are **non-retryable** (§9.1); concurrency race on the one-override invariant is the DB `UNIQUE (tenant_id, decision_record_id)` backstop (V11).

**P2 SuspendOrganizationCommand** — sync (first P2 admin write after the deferred backlog)
- Input: `tenantId, actor` (all required; actor must be `TENANT_ADMIN` — `UNAUTHORIZED` otherwise; the organization id **is** the tenant root, so a single `tenantId` is the complete scope — no separate `organizationId`)
- Logic: resolve the tenant-root organization via `OrganizationRepository.findById(tenantId)` (`ORGANIZATION_NOT_FOUND` for missing/cross-tenant); apply the domain transition `Organization.suspend()` (ACTIVE→SUSPENDED); a `DomainException` for an already-suspended organization maps to `ORGANIZATION_ALREADY_SUSPENDED` (non-retryable, §9.1); persist via the existing `save()` path
- Ports: `OrganizationRepository` (internal / same-DB only)
- Tx: one short tx (`save()`)
- Output: `SuspendOrganizationResult(tenantId, status)` (`status` = resulting `SUSPENDED`)
- **Non-idempotent (D4/§10)**: carries **no** `IdempotencyKey` and is never replayed; a repeat resolves to `ORGANIZATION_ALREADY_SUSPENDED`. One shot only.
- **Semantics (D5/D6)**: publishes **no** domain event (no authoritative consumer/audit need — §12) and adds **no** `updated_at`/timestamp behavior; the domain `Organization` aggregate and all migrations are untouched.

**P2 ArchiveRepositoryCommand** — sync (P2 admin write)
- Input: `tenantId, repositoryId, actor` (all required; actor must be `TENANT_ADMIN` — `UNAUTHORIZED` otherwise; the repository is a tenant-scoped child aggregate, so the command carries both `tenantId` and `repositoryId`)
- Logic: resolve the tenant-scoped repository via `RepositoryRepository.findByTenantIdAndId(tenantId, repositoryId)` (`REPOSITORY_NOT_FOUND` for missing/cross-tenant); apply the domain transition `Repository.archive()` (ACTIVE→ARCHIVED); a `DomainException` for an already-archived repository maps to `REPOSITORY_ALREADY_ARCHIVED` (non-retryable, §9.1); persist via the existing `save()` path
- Ports: `RepositoryRepository` (internal / same-DB only)
- Tx: one short tx (`save()`)
- Output: `ArchiveRepositoryResult(repositoryId, status)` (`status` = resulting `ARCHIVED`)
- **Non-idempotent (§10)**: carries **no** `IdempotencyKey` and is never replayed; a repeat resolves to `REPOSITORY_ALREADY_ARCHIVED`. One shot only.
- **Semantics**: publishes **no** domain event (no authoritative consumer/audit need — §12) and makes no external SCM calls; the domain `Repository` aggregate and all migrations are untouched.

### Queries (P0)

| Query | Input | Output view | Notes |
|-------|-------|-------------|-------|
| UC-11 GetChange | `changeId` (or `tenantId, repositoryId, providerChangeId`) | `ChangeAnalysisView(change, runs[history], latest risk, latest decision)` | Aggregate read; back-compat history of prior commit SHAs (`use-cases.md` §6.1) |
| UC-12 GetAnalysisRun | `analysisRunId` | `AnalysisRunView(run, riskAssessmentId, decisionId, failure)` | Surface for the 202 tracking URL |
| UC-13 GetOrganization | `tenantId` | `Organization` | |
| UC-14 GetRepository | `repositoryId` | `Repository` | |
| UC-15 GetService | `serviceId` | `Service` | |
| UC-16 GetPolicy | `policyId` | `Policy` | Active version is the policy's own version |

Rules: queries never call external ports; queries never mutate; query services never open write transactions (open a read-only read or none). Queries are not idempotency-keyed.

**Sanctioned exception (ADR-008)**: the P2 SearchEvidence query (use-cases.md §6.2, the first P2 capability) is the one query that *does* call an external port — `EvidenceSearchPort.searchByQuery(tenantId, query, limit)`. It is synchronous and read-only, and its failure contract degrades to zero evidence (`SearchEvidenceResult.degraded = true`) instead of raising an application error (ports-and-adapters.md §4), so the "never external" rule's safety intent is preserved. All other queries remain external-port-free.

P1 commands (UC-05..UC-10), UC-17/18 queries: same shape; detailed later. OverrideDecision must call `DecisionRecord` builder path and attach `HumanOverride` (the current `DecisionRecord` aggregate is treated as append/create-with-override, never mutated-in-place — see `domain-model.md` §H invariant).

---

## 7. Ports

Interfaces live in `com.cdi.application.common.port`. Conceptual signatures (using domain types). The Application layer calls these; adapters (future `*.adapter` packages) implement them and are the **only** place allowed to touch provider SDKs/persistence/queues.

| Port | Purpose | P0 caller | Conceptual methods | Failure policy (from `ports-and-adapters.md` §4) |
|------|---------|-----------|--------------------|---------------------------------------------------|
| `SourceControlPort` | Fetch PR/diff metadata, publish checks | AnalyzeChange, GenerateDecision | `ChangeMetadata getChangeMetadata(tenantId, repositoryId, providerChangeId)`; `List<FileDiff> getDiff(tenantId, repositoryId, commitSha)`; `void publishStatusCheck(tenantId, repositoryId, commitSha, DecisionOutcome, List<DecisionReason>, String detailsUrl)` | Rate-limit/5xx → exponential backoff (max 3). Failure → analysis FAILED, manual review required |
| `SystemContextPort` | Service boundaries, criticality | AnalyzeChange | `CriticalityTier getServiceCriticality(tenantId, repositoryId, List<String> filePaths)`; `List<ServiceDependency> getDependencies(tenantId, serviceId)` | Catalog unavailable → short backoff (max 3) → degrade: criticality `UNKNOWN` treated as high-risk |
| `EvidenceSearchPort` | Historical truth (safe), semantic later | AnalyzeChange; **SearchEvidence (P2, ADR-008)** | `List<EvidenceRecord> searchSimilarChanges(tenantId, filePaths, limit)`; `List<EvidenceRecord> searchIncidents(tenantId, serviceId, keywords, limit)`; `List<EvidenceRecord> searchByQuery(tenantId, query, limit)` — deterministic SQL `ILIKE` over title/content (B1), ordered `capturedAt` asc + `EvidenceId` tie-breaker (B3), top-N (D3) | Timeout → no retry → degrade: zero evidence / `degraded=true`, `EvidenceState` reflects missing; deterministic signals only |
| `AgentPort` | LLM investigation loop | — (declared now, first user UC-05, P1) | `InvestigationFindings investigate(AgentContext, RiskAssessment, List<EvidenceRecord>)` | Timeout/bad schema → 1 retry, 2-min timeout → degrade: investigation FAILED, deterministic risk only |
| `JobQueuePort` | Enqueue/cancel async commands | ProposeChange, AnalyzeChange, GenerateDecision | `JobId enqueue(String commandName, Object payload, IdempotencyKey key)`; `void cancel(IdempotencyKey key)` | DB down → bubble up → webhook 500 (SCM retries delivery) |

**Rules**
- Ports are **internal** (JobQueue, DB-backed) or **external** (the other four). Only internal ports may participate in a transaction (Section 8).
- Follow the "do not import provider SDKs" constraint; application call sites consume only these interfaces and domain types.
- The queue must support both **durable** messages and exactly-once-ish task claiming (PostgreSQL-backed queue row + state CAS; no Kafka — ADR-004).
- `RiskAssessmentPort` and `PolicyPort` are intentionally **not** defined (deterministic domain services, `ports-and-adapters.md` §3).

---

## 8. Transaction Boundaries

**Rules**
1. A database transaction is **short** and **DB-local**: it spans only persistence of aggregate state + outbox event + (same-DB) job row.
2. **Never** hold an open transaction across an external port call (SCM HTTP, LLM, vector search). This is the hard rule from `use-cases.md` §1.2.
3. Long-running orchestration (`AnalyzeChange`) uses **no global transaction**; each step commits independently. This gives checkpoints so a crash never forces a full re-run, and matches `analysis-workflow.md` §1.2 retry semantics.
4. External **side effects** (`SourceControlPort.publishStatusCheck`) are delivered by the outbox **after** commit (Section 12), not fired inside a transaction.
5. `JobQueuePort.enqueue` is same-DB and may sit inside the same transaction as the state change — this gives atomic "persist + schedule" without a distributed transaction.

**Per use case**

| Use case | Transaction(s) |
|----------|----------------|
| UC-01 ProposeChange | 1 short tx: insert `Change` + `AnalysisRun` + outbox (`ChangeProposed`) + job row |
| UC-03 AnalyzeChange | No global tx. Step tx A: claim run (CAS). External calls (no tx). Step tx B: persist `RiskAssessment` + mark run COMPLETED + enqueue `GenerateDecision` + outbox (`RiskAssessed`, `AnalysisCompleted`) |
| UC-04 GenerateDecision | 1 short tx: persist `DecisionRecord` + outbox (`DecisionGenerated`). Status check elsewhere (outbox consumer) |
| UC-02 RequestAnalysis | 1 short tx: idempotent resolution (mostly read-only); re-activate `analysis_job` only in the FAILED retry case |
| Queries | Read-only; no write tx |

Rationale: keeping the decision/risk rows in separate small commits preserves audit trails (old commit records retained) and makes failure recovery per-stage.

---

## 9. Failure Model

### 9.1 Canonical error catalog (`ApplicationError`)

The Application layer raises **exactly one** `ApplicationException(ApplicationError, details)` per failure. Errors align with `use-cases.md` §3.

| Error | Meaning / trigger | Retryable? | HTTP mapping (API layer) |
|-------|-------------------|------------|--------------------------|
| `CHANGE_NOT_FOUND` | No change for the given ids | no | 404 |
| `ANALYSIS_ALREADY_RUNNING` | Reserved. Idempotent replay **reuses** an active run instead (Section 10); raised only for a non-duplicate operation that conflicts with an in-flight run (e.g., P1 forced re-run while `RUNNING`) | no | 409 |
| `ANALYSIS_SUPERSEDED` | A newer commit invalidated this analysis | no | 409 |
| `INSUFFICIENT_CONTEXT` | Required architecture metadata missing (fatal variant; most missing context degrades instead) | no | 422 |
| `EVIDENCE_COLLECTION_FAILED` | RAG/search/vector failure that cannot degrade | no | 503/502 |
| `AGENT_INVESTIGATION_FAILED` | LLM timeout/structural validation failed (P1) | 1 retry | 502 |
| `POLICY_EVALUATION_FAILED` | Policy syntax/variables error | no | 422 |
| `DECISION_NOT_FOUND` | No decision exists for the given tenant/run, or the supplied decision id does not match (UC-06 OverrideDecision) | no | 404 |
| `DECISION_ALREADY_OVERRIDDEN` | The decision already has its one override (UC-06) — repeat is rejected, never replayed | no | 409 |
| `ORGANIZATION_ALREADY_SUSPENDED` | The organization (tenant) is already suspended (P2 SuspendOrganization) — repeat is rejected, never replayed | no | 409 |
| `REPOSITORY_ALREADY_ARCHIVED` | The repository is already archived (P2 ArchiveRepository) — repeat is rejected, never replayed | no | 409 |
| `UNAUTHORIZED` | Actor lacks required role | no | 403 |

### 9.2 Internal worker classification

Workers translate **port exceptions → stage outcome** (never leak raw adapter exceptions):

| Port failure | Worker action |
|--------------|---------------|
| SourceControlPort (SCM API) | 3 retries, backoff ≤ 5 min; then terminal → `AnalysisFailure(category: ANALYSIS_FAILED or SOURCE_UNAVAILABLE, failureCode, failedAt)` → run FAILED (fail-safe: "Manual Review Required" status) |
| SystemContextPort | 3 short retries → degrade: `CriticalityTier.UNKNOWN` treated high-risk; proceed |
| EvidenceSearchPort | no retry → degrade: empty evidence, `EvidenceState` reflects missing |
| AgentPort (P1) | 1 retry, 2-min timeout → degrade: investigation FAILED, policy on deterministic risk only |
| JobQueuePort | bubble up → webhook 500 (SCM delivery retry) |

**Fail-safe principle** (`analysis-workflow.md` §7): any worker failure ultimately results in a **visible, non-silent** outcome — either a failed run recorded with `AnalysisFailure` or a conservative `REVIEW_REQUIRED`/`BLOCK` decision. The system never approves silence. A 10-minute pipeline hard timeout marks the run `FAILED`/`SUPERSEDED` with a "System Error — Manual Review Required" status.

**Role enforcement** (`use-cases.md` §2): commands/queries carry `Actor`; the layer checks role and raises `UNAUTHORIZED` on mismatch (e.g., `OverrideDecision` requires `TENANT_ADMIN`, queue-consume commands require `SYSTEM_WORKER`).

---

## 10. Idempotency

**Placement decision**: idempotency is owned by the **Application layer** (handlers), not re-implemented in domain or persistence. Rationale: the keys are *business* identities (`hash(TenantId, RepositoryId, ProviderChangeId, CommitSHA)` for intake — `analysis-workflow.md` §3; `(tenantId, changeId, commitSha)` for `RequestAnalysis` — `api-contract.md` §3.3), which are use-case concerns. The persistence layer's only role is the final safety net: `analysis_job.UNIQUE(tenant_id, idempotency_key)` and `analysis_run.UNIQUE(tenant_id, change_id, commit_sha)` (`data-model.md` §5).

| Use case | Mechanism |
|----------|-----------|
| UC-01 ProposeChange | Compute `IdempotencyKey(tenantId, repositoryId, providerChangeId, commitSha)`. On duplicate (same PR + same commit already present) return the **existing** `analysisRunId` with `created=false` (no new run). HTTP `Idempotency-Key` header from `api-contract.md` is the transport expression; the application key is the semantic one. |
| UC-03/UC-04 (workers) | Queue guarantees at-least-once. Handlers are **replay-safe**: run claim via CAS `QUEUED→RUNNING` (UC-03) and decision generation guarded by "no decision for run yet" check. Double execution is a no-op. |
| UC-02 RequestAnalysis | **Idempotent** (corrected). Key `IdempotencyKey.analyze(tenantId, changeId, commitSha)` — see Section 6 UC-02. Always resolves by `(tenant, change, commit)` (`data-model.md` §5 uniqueness) and never duplicates a run; crosses all five run states; FAILED retry collapses concurrent requests to one job via `analysis_job.UNIQUE(tenant_id, idempotency_key)`. Concurrency protection (worker CAS) is separate and additive, not a substitute. |
| UC-06 OverrideDecision | **Non-idempotent** (frozen D3, ADR-006). Carries **no** `IdempotencyKey` — an override is a distinct, auditable human action and is never safe to replay. A second invocation for the same decision resolves to `DECISION_ALREADY_OVERRIDDEN`. The one-override invariant's race-safe backstop is `human_override.UNIQUE (tenant_id, decision_record_id)` (V11). |
| P2 SuspendOrganization | **Non-idempotent** (D4). Carries **no** `IdempotencyKey` — a suspension is a distinct admin state change and is never safe to replay. A second invocation for the same organization resolves to `ORGANIZATION_ALREADY_SUSPENDED`. Repeat is rejected by the domain guard `Organization.suspend()` (already-SUSPENDED throws `DomainException`); no DB uniqueness net is needed since the transition is a status update on the single tenant-root row. |
| P2 ArchiveRepository | **Non-idempotent**. Carries **no** `IdempotencyKey` — an archive is a distinct admin state change and is never safe to replay. A second invocation for the same repository resolves to `REPOSITORY_ALREADY_ARCHIVED`. Repeat is rejected by the domain guard `Repository.archive()` (already-ARCHIVED throws `DomainException`); no DB uniqueness net is needed since the transition is a status update on the repository row. |

Rules: queries are never keyed; idempotency keys are persisted (same row/tx as the aggregate write) so duplicate detection is race-safe; a rejected duplicate never cascades to enqueue a second job.

---

## 11. Concurrency

Three writers can touch the same `AnalysisRun`/job: the **intake** (new commit supersedes), **RequestAnalysis** (idempotent re-analysis), and the **worker** (executes a run). Strategy:

1. **Single mutator per run** — all state transitions go through domain methods (`AnalysisRun.start()`, `.complete()`, `.fail()`, `.supersede()`, `Change.updateLatestCommit()`), each guarded against invalid transitions. `RequestAnalysis` only creates a run when no existing run exists for `(tenant, change, commit)`; otherwise it reuses (Section 6 UC-02).
2. **Claim via CAS**: worker starts with an atomic `UPDATE ... SET status='RUNNING' WHERE id=? AND status='QUEUED'`. Zero rows affected → someone else is handling this run → **no-op** (safe replay, §10).
3. **Supersede flow** (`analysis-workflow.md` §9.3): a newer commit for the same PR arrives while a run is `QUEUED/RUNNING`. Intake, in one tx: `Change.updateLatestCommit(newSha)` + `oldRun.supersede()` (allowed while QUEUED/RUNNING only — completed/failed runs remain historical) + create `AnalysisRun(QUEUED)` on new snapshot + enqueue. Worker that later claims the old run finds it no longer `QUEUED` → aborts. A `RequestAnalysis` for the superseded snapshot thereafter returns the SUPERSEDED run idempotently.
4. **History preserved**: completed/failed analyses are immutable records (never superseded), satisfying auditability (`domain-model.md` §5.2).
5. **Persistence-assist is the final safety net**: `UNIQUE (tenant_id, change_id, commit_sha)` on `analysis_run` (`data-model.md` §5) makes it impossible for two runs to ever exist for the same snapshot, even under a race. App-level idempotency (§10) and worker CAS are the first lines of defense; the constraint is the backstop — it is authoritative and not a design assumption.
6. **No locks over external calls**: because §8 forbids open transactions during port calls, no row lock is ever held while awaiting SCM/LLM/vector responses.

---

## 12. Events

**Scope**: V1 events are internal to the application (observability, audit, internal consumers). No Kafka — ADR-004. Per ADR-007, every AI-influenced decision carries trace context; P0 events carry correlation ids.

**Declaration** (records, defined in domain or `common.domain.event` packages, published via `DomainEventPublisher`). Names and payload shapes follow the canonical events in `domain-events.md`:
- `ChangeProposed` (Carrier: `tenantId, repositoryId, providerChangeId, commitSha, analysisRunId`)
- `RiskAssessed` (`analysisRunId, changeId, riskAssessmentId, riskLevel`) — per `domain-events.md` §4.2 (`RiskAssessmentId, ChangeId, RiskScore`)
- `DecisionGenerated` (`analysisRunId, changeId, decisionId, outcome, policyVersion, commitSha`) — per `domain-events.md` §4.4 (`DecisionId, ChangeId, CommitSHA, Outcome, PolicyVersionId`)
- `DecisionOverridden` (`analysisRunId, changeId, decisionId, originalOutcome, outcome, commitSha`; occurredOn = `override_at`) — per `domain-events.md` §4.6; published by UC-06 OverrideDecision in the same transaction as the override row (ADR-006)
- `AnalysisCompleted` / `AnalysisFailed` — supplementary run-lifecycle events for audit only (`domain-events.md` §5 rejected the *too-granular* per-sub-step `ChangeAnalysisStarted`/`EvidenceCollected`; these terminal lifecycle events are coarser and kept out of the reject list)
- `AnalysisStarted` (optional observability aid — not part of the canonical set; add only when a consumer exists)

P0 **minimum set actually emitted**: `ChangeProposed`, `RiskAssessed`, `AnalysisCompleted`, `AnalysisFailed`, `DecisionGenerated`. Rule: publish only what has a consumer or a stated audit need.

**Delivery mechanics**:
- **Transactional outbox**: an event row is inserted in the **same DB transaction** as the state change that produced it; a dispatcher delivers after commit (`domain-events.md` outbox pattern). This guarantees "event iff state committed".
- `DecisionGenerated` is the trigger for the **status check side effect** — the outbox consumer calls `SourceControlPort.publishStatusCheck(...)`. This removes the publish from the write transaction (§8 rule 4) and gives retry/delivery guarantees.
- **Idempotent consumers**: every event carries a unique `EventId` (+ `CausationId` where chained); consumers deduplicate on `EventId` (matching `domain-events.md`).

**Rules for V1**: no cross-context synchronous event *requests*; events never carry rich aggregates (IDs only); event schemas are versioned (`name:v1`).

---

## 13. Extension Points (P1 / P2)

The design reserves clean seams without speculative code today:

1. **P1 — Agent investigation (UC-05)**: add `InvestigateRiskCommand` + `InvestigateRiskHandler` under `decision`/`investigation` using the already-declared `AgentPort`. `AnalyzeChangeHandler` gains a step: "risk HIGH **or** TIER_0 **or** evidence conflict **or** explicit manual request → enqueue `InvestigateRisk`, else enqueue `GenerateDecision`" (`analysis-workflow.md` §5). Findings persisted in coordination with `EvidenceRecord` citations (validated structurally before persist). On `AGENT_INVESTIGATION_FAILED`, continue with `GenerateDecision(..., degraded={true})`.
2. **P1 — OverrideDecision (UC-06)**: `OverrideDecisionHandler` uses the existing immutable `DecisionRecord.withOverride(HumanOverride)` (creates a new instance preserving `originalOutcome` + `newOutcome` — `policy-decision-domain.md` §10); publish `DecisionOverridden`. Requires `TENANT_ADMIN`. No changes to `GenerateDecisionHandler`.
3. **P1 — Admin setup (UC-07..10, UC-17/18)**: add the `Create*` handlers and list queries under existing packages; reuse existing aggregate builders (`Policy` requires ≥1 rule; `Service` requires `CriticalityTier`). No new ports.
4. **P1 — Semantic evidence**: `EvidenceSearchPort` adapter swaps for a pgvector-backed one; application surfaces unchanged (port hides the change).
5. **P2 — Outcome/deployment/ML attribution**: new bounded contexts; `EvidenceSearchPort`/`AgentPort` extensions only — existing handlers untouched.
6. **P2 — Externalized IDs / multi-commit UX**: `Change` handle multiple sequential commits as separate evaluations of one PR (open question in `domain-model.md` §8) — handled by the supersede flow already designed; no restructuring needed.
7. **Observability**: `AnalysisId` (=`analysisRunId`) is the primary correlation ID (ADR + `analysis-workflow.md` §8) and is present on every event/command from day one.

**Explicit non-goals to protect**: do not add a `RiskAssessmentPort`/`PolicyPort` (§3 of ports doc), do not introduce CQRS read models or separate databases, do not add a message broker, do not put event publishing into domain logic (domain is persistence-unaware).

---

## Appendix A — Architecture Review Checklist

- [x] Application layer owns **no SQL / JPA / HTTP / provider-SDK code** in design — verified by package inventory (Section 2).
- [x] No new ports beyond the five canonical ones; deterministic engines called directly, not ported (Section 7).
- [x] External calls never inside a transaction; outbox covers all external side effects (Section 8/12).
- [x] CQS separation with no CQRS infrastructure (Section 6).
- [x] Failure contract matches `use-cases.md` §3 and workflow fail-safe semantics (Section 9).
- [x] Idempotency and concurrency semantics match `analysis-workflow.md` §3, `api-contract.md` §3.3/§5, `data-model.md` §5 (both uniqueness constraints), and the `AnalysisRun` state machine (Section 10/11).
- [x] `RequestAnalysis` is **idempotent** and never duplicates a run; concurrency protection is separate and additive; DB uniqueness is the final net (Section 6 UC-02, §10, §11).
- [x] `AnalyzeChange` orchestration boundary documented — application coordinates, domain services decide, ports isolate externals; no god-service allowed (Section 5.1).
- [x] Events: internal-only, outbox-backed, idempotent consumers, no Kafka (ADR-004, ADR-007) (Section 12).
- [x] Domain model untouched; P0 slice uses existing domain invariants (Section 5).
- [x] KISS/DRY/SOLID/YAGNI: no speculative classes; `evidence` and `decision.GetDecisionByRunQueryService` packages/classes are dropped from P0.
- [x] Complemented by, not contradicting, `use-cases.md` orchestrator boundaries (ReceiveChange == ProposeChange, AnalyzeChange, GenerateDecision).

## Appendix B — Open Questions (for implementation phase, not blockers)

1. ~~DB constraint shape for "one active run per change snapshot"~~ **Resolved**: `UNIQUE (tenant_id, change_id, commit_sha)` on `analysis_run` is authoritative (`data-model.md` §5); adopted as the final safety net (Section 11.5).
2. Exact outbox dispatcher scheduling (JDBC poller vs Spring transaction synchronisation) — an adapter decision.
3. ~~Whether `FileDiff` is added to `change.domain` exactly as `domain-model.md` §B specifies~~ **Resolved**: `FileDiff` is a **required** domain value object (determination A) with minimal responsibility `(path, additions, deletions, changeType)`; belongs with the `AnalysisRun` snapshot, not on the `Change` aggregate; deferred to P0 implementation, not created here (Section 5).
4. ~~Whether P0 `RequestAnalysis` should also support re-analysis of a specific past commit~~ **Resolved**: idempotent reuse covers any snapshot with an existing run (returns its historical record). Triggering a **new** evaluation of a non-current commit is deliberately not supported (analysis is bound to the change's current snapshot); a forced re-evaluation of the identical commit is P1 and must be an explicit, separate operation.