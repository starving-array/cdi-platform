package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ChangeId;

/**
 * Input contract for UC-11 GetChange (application-layer.md §6). Primary
 * lookup by {@code ChangeId}; the source-reference lookup form
 * {@code (tenantId, repositoryId, providerChangeId)} is added with the query
 * service implementation.
 */
public record GetChangeQuery(ChangeId changeId) {

  public GetChangeQuery {
    if (changeId == null) {
      throw new DomainException("ChangeId cannot be null");
    }
  }
}