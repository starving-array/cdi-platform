package com.cdi.attribution.adapter.in.web;

import com.cdi.application.attribution.GetAttributionSummaryQueryService;
import com.cdi.application.attribution.GetDeploymentAttributionQueryService;
import com.cdi.application.common.Actor;
import com.cdi.application.port.in.GetAttributionSummaryQuery;
import com.cdi.application.port.in.GetDeploymentAttributionQuery;
import com.cdi.attribution.adapter.in.web.AttributionWebDtos.AttributionDto;
import com.cdi.attribution.adapter.in.web.AttributionWebDtos.AttributionSummaryDto;
import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.common.adapter.in.web.DevSecurityContext;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attribution")
public class AttributionController {

  private final GetDeploymentAttributionQueryService getDeploymentAttributionQueryService;
  private final GetAttributionSummaryQueryService getAttributionSummaryQueryService;

  public AttributionController(
      GetDeploymentAttributionQueryService getDeploymentAttributionQueryService,
      GetAttributionSummaryQueryService getAttributionSummaryQueryService) {
    this.getDeploymentAttributionQueryService = getDeploymentAttributionQueryService;
    this.getAttributionSummaryQueryService = getAttributionSummaryQueryService;
  }

  @GetMapping("/{deploymentId}")
  public ResponseEntity<AttributionDto> getDeploymentAttribution(
      @PathVariable("deploymentId") UUID deploymentId,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    GetDeploymentAttributionQuery query = new GetDeploymentAttributionQuery(
        tenantId, new DeploymentId(deploymentId), actor);

    DecisionAttribution attribution = getDeploymentAttributionQueryService.handle(query);
    return ResponseEntity.ok(AttributionDto.fromDomain(attribution));
  }

  @GetMapping("/summary")
  public ResponseEntity<AttributionSummaryDto> getSummary(
      @RequestParam(value = "serviceId", required = false) UUID serviceId,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    ServiceId svcId = serviceId != null ? new ServiceId(serviceId) : null;
    GetAttributionSummaryQuery query = new GetAttributionSummaryQuery(tenantId, svcId, actor);

    AttributionSummary summary = getAttributionSummaryQueryService.handle(query);
    return ResponseEntity.ok(AttributionSummaryDto.fromDomain(summary));
  }
}