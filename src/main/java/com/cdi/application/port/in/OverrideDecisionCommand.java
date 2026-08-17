package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;

/**
 * Input contract for UC-06 OverrideDecision — the synchronous, TENANT_ADMIN-only
 * one-shot override of a persisted decision outcome (application-layer.md §6
 * UC-06/§13, ADR-006, use-cases.md §5.4). Immutable.
 *
 * <p><b>Frozen contract (D3)</b>: all six components are required. Nulls and
 * blanks are rejected using the existing command conventions; a blank
 * {@code justification} is rejected so an override is never silent
 * ({@code HumanOverride} re-validates this at domain entry,
 * policy-decision-domain.md §10). The {@code newOutcome} becomes the effective
 * decision outcome; the original outcome is captured on the override row
 * exactly as generated (never recomputed).
 *
 * <p><b>Non-idempotent (D3/§10)</b>: the command carries <em>no</em>
 * {@code IdempotencyKey}. Each invocation is a distinct, auditable
 * override attempt; a run whose decision is already overridden resolves to
 * {@code DECISION_ALREADY_OVERRIDDEN} rather than being replayed or
 * re-applied. Contrast UC-10 CreatePolicy, whose semantic duplicate check is
 * idempotent.
 */
public record OverrideDecisionCommand(
    TenantId tenantId,
    Actor actor,
    AnalysisRunId analysisRunId,
    DecisionId decisionId,
    DecisionOutcome newOutcome,
    String justification) {

  public OverrideDecisionCommand {
    if (tenantId == null) {
      throw new DomainException("TenantId cannot be null");
    }
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (analysisRunId == null) {
      throw new DomainException("AnalysisRunId cannot be null");
    }
    if (decisionId == null) {
      throw new DomainException("DecisionId cannot be null");
    }
    if (newOutcome == null) {
      throw new DomainException("Outcome cannot be null");
    }
    if (justification == null || justification.isBlank()) {
      throw new DomainException("Justification cannot be blank");
    }
    justification = justification.trim();
  }
}