# ADR 006: Human Control Over Consequential Actions

## Status
Accepted

## Context
The system evaluates risk deterministically and produces a final `DecisionRecord`
outcome for every analysis run (UC-05 policy evaluation). The generated outcome —
`APPROVE`, `REVIEW_REQUIRED`, or `BLOCK` — is an immutable, auditable historical
artifact (policy-decision-domain.md §12). While automated evaluation is
fail-safe, human operators occasionally need to force a different outcome for a
consequential action (e.g., block a risky merge, or approve one after manual
inspection). Any such human change must be (a) synchronous and admin-only,
(b) fully auditable — who, what, why, when — and (c) incapable of silently
erasing the automated decision trail.

## Decision
UC-06 **OverrideDecision** is a synchronous, `TENANT_ADMIN`-only write command
(`OverrideDecisionCommand` → `OverrideDecisionHandler` → `OverrideDecisionResult`)
that records a single, immutable human override of a decision's outcome. The
accepted contract (frozen D1–D5):

- **Original decision never mutated**: the historical `decision_record` row —
  its `outcome`, reasons, required actions, and `generated_at` — is untouched. The
  override is persisted as a separate, supplemental `human_override` row that is
  a 1:1 child of `decision_record` (V11 migration). The effective outcome of the
  decision becomes the override's `new_outcome`.
- **One-shot / non-idempotent**: exactly one override per decision
  (`UNIQUE (tenant_id, decision_record_id)` backstop plus the domain guard in
  `DecisionRecord.withOverride`). A repeated override resolves to
  `DECISION_ALREADY_OVERRIDDEN`. There are no revision chains or override
  history. The command carries no `IdempotencyKey` (contrast UC-10 CreatePolicy).
- **Audit trail**: the override row records `actor_id`, `justification`
  (required — never silent), `original_outcome`, `new_outcome`, and
  `override_at` (supplementing, never replacing, `generated_at`); a
  `DecisionOverridden` domain event is published through `DomainEventPublisher`
  in the same transaction as the saved override (domain-events.md §4.6).
- **Tenant isolation**: every lookup and the unique constraint are tenant-scoped;
  a cross-tenant run or decision resolves to the corresponding NOT_FOUND error.
- **No external integration**: no controllers, no external ports, nothing
  enqueued, no workers or schedulers.

### Error mapping
`UNAUTHORIZED` (non-`TENANT_ADMIN`) · `ANALYSIS_RUN_NOT_FOUND` (missing or
cross-tenant run) · `ANALYSIS_SUPERSEDED` (superseded run) ·
`DECISION_NOT_FOUND` (missing, cross-tenant, or mismatched decision) ·
`DECISION_ALREADY_OVERRIDDEN` (repeated override). Invalid command/domain values
surface as `DomainException` via the existing command and value-object
validation conventions.

## Consequences
- Positive: override attempts are fully auditable, the automated decision trail
  is never falsified, humans retain final control over consequential actions,
  and tenant isolation is preserved on every write and lookup.
- Negative: an erroneous override cannot be silently corrected — it requires
  the same one-shot audit path; consumers reading the "effective outcome" must
  consult the supplemental override rather than the `decision_record` row alone
  (`DecisionRecord.getOutcome()` already surfaces the effective outcome).
- Rejected alternatives: mutating the original `decision_record` row (loses the
  automated trail); multiple override revisions / full history (scope creep,
  no V1 consumer); idempotency-key semantics and replay (an override is not
  replay-safe by design); repurposing the generic `POLICY_EVALUATION_FAILED`
  error instead of decision-specific errors.