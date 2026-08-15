# Application Foundation Contracts (implemented)

**Status**: Implemented — compiles with the full P0 build (`.\mvnw.cmd clean test`,
BUILD SUCCESS). This document covers the minimal shared Application-layer
foundation, the **first real use case, UC-01 ProposeChange** (implemented
in `com.cdi.application.change.ProposeChangeHandler`), and the **UC-01
persistence adapters** (JPA/PostgreSQL implementations of the `ChangeRepository`
and `AnalysisRunRepository` ports). No controllers or other use cases were added.

Design source of truth: `docs/02-architecture/application-layer.md` (V1),
`docs/02-architecture/ports-and-adapters.md`, `docs/02-architecture/use-cases.md`.

## 1. Package structure (as implemented)

```
com.cdi.application
├── common
│   ├── error
│   │   ├── ApplicationError.java        # canonical error catalog (§9.1)
│   │   ├── ApplicationException.java    # carries ONE ApplicationError + details
│   │   ├── PortException.java           # outbound-port failure transport
│   │   └── PortType.java                # identifies the failed port
│   ├── result
│   │   ├── CommandResult.java           # success marker for command outcomes
│   │   └── IdempotentCommandResult.java # analysisRunId + created
│   ├── event
│   │   └── DomainEventPublisher.java    # transactional-outbox contract
│   ├── Actor.java                       # resolved actor: id + Role
│   └── IdempotencyKey.java              # opaque computed key value
└── port
    ├── in                               # inbound command/query input contracts (records)
    │   ├── ProposeChangeCommand.java
    │   ├── RequestAnalysisCommand.java
    │   ├── AnalyzeChangeCommand.java
    │   ├── GenerateDecisionCommand.java
    │   ├── GetChangeQuery.java
    │   ├── GetAnalysisRunQuery.java
    │   ├── GetOrganizationQuery.java
    │   ├── GetRepositoryQuery.java
    │   ├── GetServiceQuery.java
    │   └── GetPolicyQuery.java
    └── out                              # outbound ports (five canonical + UC-01 persistence)
        ├── SourceControlPort.java
        ├── SystemContextPort.java
        ├── EvidenceSearchPort.java
        ├── AgentPort.java
        ├── JobQueuePort.java
        ├── ChangeRepository.java        # UC-01 persistence port (Change aggregate)
        ├── AnalysisRunRepository.java   # UC-01 persistence port (AnalysisRun aggregate)
        ├── ChangeMetadata.java          # port model (provider-neutral PR info)
        ├── JobId.java                   # port model (job handle)
        ├── AgentContext.java            # port model (agent input context)
        ├── InvestigationFinding.java    # port model (finding + EvidenceId citations)
        └── InvestigationFindings.java   # port model (agent output)
```

Under `com.cdi.application`:

```
├── change
│   └── ProposeChangeHandler.java        # UC-01 ProposeChange (sync, P0)
```

Persistence adapters (implemented in their bounded-context adapter packages):

```
com.cdi.change.adapter.out.persistence      ← implements ChangeRepository
├── ChangeEntity.java                       # JPA entity for the change table
├── ChangeJpaRepository.java                # Spring Data repository
├── ChangeMapper.java                       # aggregate ↔ entity conversion
└── JpaChangeRepository.java                # CdiRepository port adapter

com.cdi.analysis.adapter.out.persistence    ← implements AnalysisRunRepository
├── AnalysisRunEntity.java                  # JPA entity for the analysis_run table
├── AnalysisRunJpaRepository.java           # Spring Data repository
├── AnalysisRunMapper.java                  # aggregate ↔ entity conversion
└── JpaAnalysisRunRepository.java           # AnalysisRunRepository port adapter
```

Plus one Flyway migration:
`src/main/resources/db/migration/V2__change_and_analysis_run.sql`.

Placement notes (deliberate, minimal deviation):
- Inbound **input** records are centralized in `port.in` (the task's expected
  structure) instead of the bounded-context packages shown in the architecture
  sketch. Handlers/query services — when implemented — still live in their
  bounded-context packages (`com.cdi.application.change.*`, etc.).
- `com.cdi.application.common.port` (architecture sketch) is realized as
  `com.cdi.application.port.out` so inbound (`port.in`) and outbound (`port.out`)
  contracts are structurally separated, matching the hexagonal port/port naming.
- No packages were created empty; `evidence`/`decision.investigation` bounded
  contexts are deferred to P1.

## 2. Error contract

`ApplicationError` is an enum with the **exact** canonical categories from
application-layer.md §9.1 / use-cases.md §3: `CHANGE_NOT_FOUND`,
`ANALYSIS_ALREADY_RUNNING`, `ANALYSIS_SUPERSEDED`, `INSUFFICIENT_CONTEXT`,
`EVIDENCE_COLLECTION_FAILED`, `AGENT_INVESTIGATION_FAILED`,
`POLICY_EVALUATION_FAILED`, `UNAUTHORIZED`. No new categories were invented.

Each error exposes:
- `code()` — the stable enum name;
- `defaultMessage()` — human-readable default message;
- `retryable()` — retryable/non-retryable classification per the documented
  policy (only `AGENT_INVESTIGATION_FAILED` is retryable, matching §9.1).

`ApplicationException` carries exactly one `ApplicationError` plus an optional
immutable `Map<String, ?>` of structured details. The Application layer never
leaks HTTP/Spring/JPA/provider exceptions to the API layer.

## 3. Result contract

- `CommandResult` — marker interface for **success** outcomes; failures are
  modelled as `ApplicationException`, so the wrapper stays failure-free and
  HTTP-agnostic.
- `IdempotentCommandResult(analysisRunId, created)` — the P0 result shape
  (intake and manual re-analysis).

## 4. Outbound port contracts (the five canonical ports)

Signatures follow application-layer.md §7 / ports-and-adapters.md §2. They use
only domain types and port model records above; no provider/SDK/HTTP/JPA types.

| Port | Methods | Failure policy (documented on the interface) |
|------|---------|---------------------------------------------|
| `SourceControlPort` | `getChangeMetadata`, `getDiff`, `publishStatusCheck` | Retryable (exp. backoff max 3); hard-fail analysis when source data unobtainable |
| `SystemContextPort` | `getServiceCriticality`, `getDependencies` | Retryable (short backoff max 3); degrade, unknown criticality treated high-risk |
| `EvidenceSearchPort` | `searchSimilarChanges`, `searchIncidents` | No retry; graceful degradation to zero evidence |
| `AgentPort` | `investigate` | Exactly one retry; degrade to deterministic risk only |
| `JobQueuePort` | `enqueue`, `cancel` | DB down bubbles up (webhook 500 / SCM retry); `cancel` marks pending/running job obsolete |

No `RiskAssessmentPort` or `PolicyPort` exist (deterministic in-process domain
services — ports-and-adapters.md §3).

## 5. Failure transport to the orchestration layer

Ports communicate failures via `PortException` (unchecked), which exposes
`PortType` (which port failed) and `isRetryable()`. **No retry loops are
implemented anywhere** — the orchestration layer applies the documented
retry/degradation policy per call site at use-case implementation time.

## 6. Inbound port contracts and UC-01 ProposeChange

Immutable Java records named `<UseCase>Command` / `<UseCase>Query` per the
application-layer naming conventions. Inputs carry strong domain identities
(`TenantId`, `RepositoryId`, `ChangeId`, `AnalysisRunId`, ...), `Actor`, and
`IdempotencyKey` where the design requires them.

**UC-01 ProposeChange is implemented** (`com.cdi.application.change.ProposeChangeHandler`):
- Orchestrates the existing `Change`/`AnalysisRun` aggregates and the outbound
  ports (`ChangeRepository`, `AnalysisRunRepository`, `JobQueuePort`,
  `DomainEventPublisher`); it never re-implements domain rules.
- Idempotency (§10): an existing change with the same natural key
  `(tenantId, repositoryId, providerChangeId)` at the same `commitSha`
  resolves to the existing run with `created=false` — no duplicate, no event,
  no enqueue. A new commit on an existing OPEN change updates its latest
  commit and queues a new snapshot run.
- Role guard (§9.1): only `ENGINEER` / `SYSTEM_WORKER` may propose changes.
- Errors: `DomainException` from domain invariants (e.g., a new commit on a
  MERGED/CLOSED change) is translated to
  `ApplicationException(CHANGE_NOT_FOUND, details)`; the API layer never sees
  raw domain/persistence/queue exceptions. JobQueue failures bubble up via the
  port failure policy (webhook 500).
- Emits `ChangeProposed` (new domain event, `common.domain.event`) and enqueues
  `AnalyzeChangeCommand` only when new state is actually created.

`GetChangeQuery` currently exposes only the `ChangeId` lookup; the
source-reference form `(tenantId, repositoryId, providerChangeId)` is added
together with the `GetChangeQueryService` implementation.

## 7. Domain dependency rule

Dependency direction verified: `Domain ⇠ Application ⇠ Adapters`.
- Application (new) depends on `com.cdi.*.domain` and `com.cdi.common.domain.*`.
- The two domain-aggregate hydration additions were **additions**, not
  semantic changes: `Change.restore(...)` and `AnalysisRun.restore(...)`
  (framework-independent static factories that rebuild an aggregate exactly as
  persisted, including status/timestamps; they perform no state transitions and
  touch no JPA/SQL/Spring types). No existing domain semantics changed.
- One domain value object was earlier **added**, not modified:
  `com.cdi.analysis.domain.FileDiff` (determination A, application-layer.md §5,
  confirmed KEEP by the FileDiff scope audit). It is a required domain
  prerequisite, not an application DTO: `domain-model.md` §B lists it as a
  Change-context VO (path, additions, deletions, change type); `analysis-workflow.md`
  §1.2 normalizes SCM diffs into `FileDiff` objects and §1.3 feeds them into context
  collection; `data-model.md` §B persists them as `changed_file(...)`; and
  `SourceControlPort.getDiff(...)` (already part of the approved port table,
  application-layer.md §9) must return `List<FileDiff>`. It belongs with the snapshot
  side of `AnalysisRun`. No existing domain semantics changed.

### 7.1 UC-01 persistence schema (documented deviation)

`data-model.md` §B lists a simplified `change` shape
(`id, tenant_id, repo_uri, pr_id, status`). The implemented `Change` aggregate
tracks more state that must round-trip for UC-01 idempotency
(`application-layer.md` §6 compares `latestCommitSha` for duplicate detection),
so the `change` table carries the **full aggregate columns**: `id, tenant_id,
repository_id, provider_change_id, title, description, author, source_branch,
target_branch, latest_commit_sha, status, created_at, updated_at`. Resolved
with the architecture owner during implementation (approved deviation).

Everything else follows `data-model.md` faithfully:
- `tenant_id` on every row (§2) — strong per-tenant isolation.
- `analysis_run` = `id, tenant_id, change_id, commit_sha, branch, status,
  failure_category, failure_code, failed_at, created_at, completed_at`, with
  `UNIQUE (tenant_id, change_id, commit_sha)` as the final idempotency safety
  net (§5; application-layer.md §10/§11.5).
- Tenant-scoped composite FK `(tenant_id, change_id) → change (tenant_id, id)`
  so a run can only reference a change of the same tenant (§5).
- `change` uniqueness `UNIQUE (tenant_id, repository_id, provider_change_id)`
  backs the UC-01 natural-key duplicate detection (§5).
- `created_at`/`updated_at` audit fields on both tables (§6).

`CodeSnapshot` flattens to `commit_sha`/`branch` (data-model.md §7:
value objects embedded 1:1 in the aggregate table). Domain reads back via the
`restore(...)` factories; the handler/ports are unchanged.

## 8. Tests added

Focused tests only (no coverage padding):
- `ApplicationErrorTest` — catalog coverage, stable codes, retryability.
- `ApplicationExceptionTest` — single-error invariant, message override,
  structured details, immutability.
- `PortExceptionTest` — port identity + retryable flag.
- `ActorTest`, `IdempotencyKeyTest` — value-object invariants.
- `IdempotentCommandResultTest` — created/idempotent reuse.
- `InboundCommandQueryContractTest` — command/query construction & rejection.
- `FileDiffTest` — new domain VO invariants.
- `PortContractTest` — provider-neutral fake adapters implement all five ports
  end-to-end using only domain/application types (validates contract usability
  and the application→port→adapter direction).
- `ProposeChangeHandlerTest` — UC-01 orchestration: change/run creation and
  state, field preservation, idempotent reuse (`created=false`, no duplicate),
  new-commit-on-existing-OPEN flow, invalid-input rejection, domain-invariant
  translation, role enforcement, enqueue payload + key, event emission.

Persistence integration tests (Testcontainers, reuse the shared
`PostgresTestContainerConfiguration`; JPA entity schema is validated against the
Flyway-applied schema via `ddl-auto: validate`):
- `ChangePersistenceIntegrationTest` — full-field round-trip, missing natural
  key, tenant isolation, new-commit update cycle, non-OPEN status round-trip,
  DB `UNIQUE` enforcement.
- `AnalysisRunPersistenceIntegrationTest` — queued-run round-trip, missing
  snapshot, tenant isolation, RUNNING→COMPLETED cycle with `completedAt`,
  FAILED round-trip with failure category/code/code-time, DB `UNIQUE`
  enforcement.
- `ProposeChangePersistenceIntegrationTest` — end-to-end UC-01 with the real
  JPA adapters (fakes only at event-publisher and job-queue boundaries):
  proposal persists both aggregates, cross-process idempotent reuse returns the
  existing run with no extra rows, and a new commit on a persisted OPEN change
  creates a second run and updates `latestCommitSha`.

## 9. Explicit non-goals (unchanged, updated exceptions)

No Business use cases beyond UC-01, no Application services beyond
`ProposeChangeHandler`, no REST controllers beyond `HealthController`, no
GitHub/LLM adapters, no job worker, no Kafka/Redis, no retry frameworks, no
outbox consumer. Application layer owns no SQL/JPA/HTTP/provider code.

**Persistence exceptions now implemented** (the two UC-01 repository ports and
their JPA adapters, §7.1): `ChangeRepository` and `AnalysisRunRepository` are
backed by `JpaChangeRepository`/`JpaAnalysisRunRepository` over the `change` and
`analysis_run` tables (Flyway `V2__change_and_analysis_run.sql`). That is the
intentional end of the persistence slice — the remaining tables
(`changed_file`, `evidence*`, `risk_*`, `policy_*`, `decision_*`,
`investigation_*`, `analysis_job`, `service*`, `tenant`, `organization`,
`repository`) are **not** created, as their use cases are not yet implemented.

Domain layer untouched except the `FileDiff` addition, the `ChangeProposed`
event declaration, and a single `restore(...)` hydration addition to each of
`Change` and `AnalysisRun` — all additions, not modifications (§7). UC-01
ProposeChange is the **only** implemented use case; UC-02..UC-18 handlers/query
services remain to be built.