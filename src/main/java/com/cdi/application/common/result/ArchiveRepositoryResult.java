package com.cdi.application.common.result;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.repository.domain.Repository;

/**
 * Command result for the P2 ArchiveRepository capability: the repository
 * id and the resulting {@link Repository.Status} after the archive was
 * persisted (application-layer.md §6).
 *
 * <p>The status returned is the post-transition state ({@code ARCHIVED});
 * the result carries the repository identity (organization-repository-service-domain.md,
 * data-model.md §3).
 */
public record ArchiveRepositoryResult(
    RepositoryId repositoryId,
    Repository.Status status) implements CommandResult {

  public ArchiveRepositoryResult {
    if (repositoryId == null) {
      throw new DomainException("RepositoryId cannot be null");
    }
    if (status == null) {
      throw new DomainException("Status cannot be null");
    }
  }
}
