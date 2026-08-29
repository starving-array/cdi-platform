package com.cdi.repository.adapter.in.web;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.result.CreateRepositoryResult;
import com.cdi.application.port.in.CreateRepositoryCommand;
import com.cdi.application.repository.CreateRepositoryHandler;
import com.cdi.common.adapter.in.web.DevSecurityContext;
import com.cdi.common.domain.id.TenantId;
import com.cdi.repository.domain.Repository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Web Controller exposing Repository capabilities.
 */
@RestController
@RequestMapping("/api/v1/repositories")
public final class RepositoryController {

  /** The handler for creating repositories. */
  private final CreateRepositoryHandler createRepositoryHandler;

  /**
   * Constructs the controller.
   *
   * @param handler the handler
   */
  public RepositoryController(final CreateRepositoryHandler handler) {
    this.createRepositoryHandler = handler;
  }

  /**
   * Creates a repository.
   *
   * @param request the request body
   * @param httpRequest the http request
   * @return the response entity
   */
  @PostMapping
  public ResponseEntity<RepositoryWebDtos.CreateRepositoryResponse> createRepository(
      @RequestBody final RepositoryWebDtos.CreateRepositoryRequest request,
      final HttpServletRequest httpRequest) {

    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    Repository.ProviderType providerType;
    try {
      providerType = Repository.ProviderType.valueOf(request.providerType());
    } catch (IllegalArgumentException | NullPointerException e) {
      providerType = null;
    }

    String idempotencyStr = tenantId.value() + ":" 
        + request.providerType() + ":" + request.externalId();
    IdempotencyKey idempotencyKey = new IdempotencyKey(idempotencyStr);

    CreateRepositoryCommand command = new CreateRepositoryCommand(
        tenantId,
        providerType,
        request.externalId(),
        request.name(),
        request.url(),
        request.defaultBranch(),
        actor,
        idempotencyKey);

    CreateRepositoryResult result = createRepositoryHandler.handle(command);
    
    RepositoryWebDtos.CreateRepositoryResponse response = 
        new RepositoryWebDtos.CreateRepositoryResponse(
            result.repositoryId().value(),
            result.created());

    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(response);
  }
}
