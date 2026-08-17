package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.decision.domain.DecisionOutcome;

/**
 * Command result for UC-06 OverrideDecision (application-layer.md §6 UC-06,
 * ADR-006): the overridden decision, its immutable original outcome, and its
 * new effective outcome.
 *
 * <p>Carries the {@link DecisionId} and both outcomes. The decision belongs to
 * a tenant (data-model.md §2); the tenant identity is established upstream by
 * the command's {@code TenantId} and is not duplicated on this result. Mirrors
 * {@code CreatePolicyResult}/{@code CreateServiceResult}.
 */
public record OverrideDecisionResult(
    DecisionId decisionId,
    DecisionOutcome originalOutcome,
    DecisionOutcome outcome) implements CommandResult {

  public OverrideDecisionResult {
    if (decisionId == null) {
      throw new DomainException("DecisionId cannot be null");
    }
    if (originalOutcome == null) {
      throw new DomainException("OriginalOutcome cannot be null");
    }
    if (outcome == null) {
      throw new DomainException("Outcome cannot be null");
    }
  }
}