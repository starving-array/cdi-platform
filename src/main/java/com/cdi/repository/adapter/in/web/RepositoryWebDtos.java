package com.cdi.repository.adapter.in.web;

import java.util.UUID;

public class RepositoryWebDtos {

  public record CreateRepositoryRequest(
      String providerType,
      String externalId,
      String name,
      String url,
      String defaultBranch) {
  }

  public record CreateRepositoryResponse(
      UUID repositoryId,
      boolean created) {
  }
}
