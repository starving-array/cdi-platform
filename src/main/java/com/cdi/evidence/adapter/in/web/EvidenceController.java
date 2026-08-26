package com.cdi.evidence.adapter.in.web;

import com.cdi.application.common.Actor;
import com.cdi.application.evidence.SearchEvidenceQueryService;
import com.cdi.application.evidence.SearchEvidenceResult;
import com.cdi.application.port.in.SearchEvidenceQuery;
import com.cdi.common.adapter.in.web.DevSecurityContext;
import com.cdi.common.domain.id.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Web Controller exposing Evidence search capabilities to the web frontend.
 */
@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

  private final SearchEvidenceQueryService searchEvidenceQueryService;

  public EvidenceController(SearchEvidenceQueryService searchEvidenceQueryService) {
    this.searchEvidenceQueryService = searchEvidenceQueryService;
  }

  @GetMapping("/search")
  public ResponseEntity<EvidenceWebDtos.SearchEvidenceResponse> searchEvidence(
      @RequestParam String query,
      @RequestParam(defaultValue = "10") int limit,
      HttpServletRequest request) {

    TenantId tenantId = DevSecurityContext.extractTenantId(request);
    Actor actor = DevSecurityContext.extractActor(request);

    SearchEvidenceQuery searchQuery = new SearchEvidenceQuery(query, java.util.Map.of(), limit);
    SearchEvidenceResult result = searchEvidenceQueryService.handle(searchQuery, tenantId, actor);

    return ResponseEntity.ok(EvidenceWebDtos.SearchEvidenceResponse.fromResult(result));
  }
}