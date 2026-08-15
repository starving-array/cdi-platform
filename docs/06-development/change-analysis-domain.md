# Change and Analysis Domain

This document summarizes the core distinction between the logical Change and its historical Analysis Runs.

## 1. Aggregate Boundaries
- **`Change`** (Aggregate Root): Represents the logical software modification (e.g., Pull Request). It tracks the overall lifecycle (OPEN/MERGED/CLOSED) and the *latest* known commit.
- **`AnalysisRun`** (Aggregate Root): Represents the evaluation of a specific code snapshot. 

*Rationale*: A single `Change` receives multiple commits over its lifetime. If `Change` contained the risk score directly, the history of evaluations would be lost. By splitting them, we guarantee historical auditability: each `AnalysisRun` points to an immutable `CodeSnapshot`.

## 2. CodeSnapshot
An immutable Value Object `CodeSnapshot(commitSha, branch)` guarantees that every analysis is tied precisely to the exact code it evaluated. It is the primary idempotency key for the domain.

## 3. State Transitions

### Change Lifecycle
- `OPEN` -> `MERGED` or `CLOSED`
- `CLOSED` -> `OPEN` (Reopen)

### AnalysisRun Lifecycle
- `QUEUED` -> `RUNNING`
- `RUNNING` -> `COMPLETED`
- `QUEUED` or `RUNNING` -> `FAILED`
- `FAILED` -> `QUEUED` (retry)
- `QUEUED` or `RUNNING` -> `SUPERSEDED`

## 4. Superseding Behavior
If a developer pushes a new commit (`def456`) while an older commit (`abc123`) is currently `RUNNING`, the older `AnalysisRun` is marked `SUPERSEDED`. 
- **Historical Immutability**: If an `AnalysisRun` is already `COMPLETED` or `FAILED`, it **cannot** be transitioned to `SUPERSEDED`. It remains permanently intact as a historical record of what the system evaluated at that moment in time.

## 5. Domain Invariants
- A `Change` must point to a `RepositoryId`.
- An `AnalysisRun` must point to a `ChangeId`.
- A `Change` cannot receive new commits (`updateLatestCommit()`) if it is already `MERGED` or `CLOSED`.
- An `AnalysisFailure` must provide a category (e.g., `SOURCE_UNAVAILABLE`) and a timestamp, preventing silent obscure failures.
