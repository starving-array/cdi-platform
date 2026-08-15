package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;

/**
 * Input contract for UC-16 GetPolicy (application-layer.md §6). The active
 * version is the policy's own current version.
 */
public record GetPolicyQuery(PolicyId policyId) {

  public GetPolicyQuery {
    if (policyId == null) {
      throw new DomainException("PolicyId cannot be null");
    }
  }
}