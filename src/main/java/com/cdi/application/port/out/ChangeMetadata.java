package com.cdi.application.port.out;

import com.cdi.common.domain.exception.DomainException;

/**
 * Provider-neutral pull request / merge request metadata returned by
 * {@link SourceControlPort}.
 *
 * <p>Abstract "PR info" (ports-and-adapters.md §2.1); adapters translate
 * provider webhooks/APIs into this standard representation.
 */
public record ChangeMetadata(
    String providerChangeId,
    String title,
    String description,
    String author,
    String sourceBranch,
    String targetBranch,
    String latestCommitSha) {

  public ChangeMetadata {
    if (providerChangeId == null || providerChangeId.isBlank()) {
      throw new DomainException("Provider change ID cannot be blank");
    }
    if (latestCommitSha == null || latestCommitSha.isBlank()) {
      throw new DomainException("Latest commit SHA cannot be blank");
    }
    providerChangeId = providerChangeId.trim();
    latestCommitSha = latestCommitSha.trim();
    title = title != null ? title.trim() : "";
    description = description != null ? description.trim() : "";
    author = author != null ? author.trim() : "";
    sourceBranch = sourceBranch != null ? sourceBranch.trim() : "";
    targetBranch = targetBranch != null ? targetBranch.trim() : "";
  }
}