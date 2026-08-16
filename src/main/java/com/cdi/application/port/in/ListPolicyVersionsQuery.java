package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;

/**
 * Input contract for UC-18 ListPolicyVersions (application-layer.md §6,
 * api-contract.md §2.3). Lists every policy version of the tenant-scoped
 * policy identified by {@code policyId} (ACTIVE + ARCHIVED), ordered
 * oldest→newest by {@code createdAt}.
 */
public record ListPolicyVersionsQuery(PolicyId policyId) {

  public ListPolicyVersionsQuery {
    if (policyId == null) {
      throw new DomainException("PolicyId cannot be null");
    }
  }
}