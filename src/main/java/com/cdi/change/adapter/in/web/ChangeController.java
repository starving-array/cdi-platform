package com.cdi.change.adapter.in.web;

import com.cdi.application.change.ChangeAnalysisView;
import com.cdi.application.change.GetChangeQueryService;
import com.cdi.application.change.ListChangesQueryService;
import com.cdi.application.common.Actor;
import com.cdi.application.port.in.GetChangeQuery;
import com.cdi.application.port.in.ListChangesQuery;
import com.cdi.change.domain.Change;
import com.cdi.common.adapter.in.web.DevSecurityContext;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Web Controller exposing Change query capabilities (ListChanges and GetChange)
 * to the web frontend.
 */
@RestController
@RequestMapping("/api/v1/changes")
public class ChangeController {

  private final ListChangesQueryService listChangesQueryService;
  private final GetChangeQueryService getChangeQueryService;

  public ChangeController(
      ListChangesQueryService listChangesQueryService,
      GetChangeQueryService getChangeQueryService) {
    this.listChangesQueryService = listChangesQueryService;
    this.getChangeQueryService = getChangeQueryService;
  }

  @GetMapping
  public ResponseEntity<List<ChangeWebDtos.ChangeSummaryResponse>> listChanges(
      @RequestParam(required = false) UUID repositoryId,
      HttpServletRequest request) {

    TenantId tenantId = DevSecurityContext.extractTenantId(request);
    Actor actor = DevSecurityContext.extractActor(request);

    ListChangesQuery query = repositoryId != null
        ? ListChangesQuery.forRepository(new RepositoryId(repositoryId))
        : ListChangesQuery.all();

    List<Change> changes = listChangesQueryService.handle(query, tenantId, actor);
    List<ChangeWebDtos.ChangeSummaryResponse> response = changes.stream()
        .map(ChangeWebDtos.ChangeSummaryResponse::fromDomain)
        .toList();

    return ResponseEntity.ok(response);
  }

  @GetMapping("/{changeId}")
  public ResponseEntity<ChangeWebDtos.ChangeDetailResponse> getChange(
      @PathVariable UUID changeId,
      HttpServletRequest request) {

    TenantId tenantId = DevSecurityContext.extractTenantId(request);
    Actor actor = DevSecurityContext.extractActor(request);

    GetChangeQuery query = new GetChangeQuery(new ChangeId(changeId));
    ChangeAnalysisView view = getChangeQueryService.handle(query, tenantId, actor);

    return ResponseEntity.ok(ChangeWebDtos.ChangeDetailResponse.fromView(view));
  }
}