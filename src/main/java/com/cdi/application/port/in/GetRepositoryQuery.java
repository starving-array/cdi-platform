package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;

/**
 * Input contract for UC-14 GetRepository (application-layer.md §6).
 */
public record GetRepositoryQuery(RepositoryId repositoryId) {

  public GetRepositoryQuery {
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
  }
}