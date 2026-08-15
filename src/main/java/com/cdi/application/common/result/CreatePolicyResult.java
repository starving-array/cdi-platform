package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;

/**
 * Idempotent command result for UC-10 CreatePolicy: the policy produced or
 * reused by the command, plus whether it was newly created
 * (application-layer.md §6).
 *
 * <p>Carries the policy {@link PolicyId}. The policy belongs to a tenant
 * (data-model.md §2); the tenant identity is established upstream by the
 * command's {@code TenantId} and is not duplicated on this result. Mirrors
 * {@code CreateServiceResult}/{@code CreateRepositoryResult}.
 */
public record CreatePolicyResult(PolicyId policyId, boolean created)
    implements CommandResult {

  public CreatePolicyResult {
    if (policyId == null) {
      throw new DomainException("PolicyId cannot be null");
    }
  }
}
