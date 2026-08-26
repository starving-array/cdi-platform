package com.cdi.application.port.in;

import com.cdi.common.domain.id.RepositoryId;

import java.util.Optional;

/**
 * Input contract for the P2 ListChanges capability (application-layer.md §6).
 * Lists changes for the tenant resolved from the request context, optionally
 * filtered by {@code repositoryId}, ordered by {@code createdAt DESC, id DESC}.
 */
public record ListChangesQuery(Optional<RepositoryId> repositoryId) {

  public ListChangesQuery {
    if (repositoryId == null) {
      repositoryId = Optional.empty();
    }
  }

  public static ListChangesQuery all() {
    return new ListChangesQuery(Optional.empty());
  }

  public static ListChangesQuery forRepository(RepositoryId repositoryId) {
    return new ListChangesQuery(Optional.ofNullable(repositoryId));
  }
}