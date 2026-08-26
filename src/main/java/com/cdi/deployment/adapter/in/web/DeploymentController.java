package com.cdi.deployment.adapter.in.web;

import com.cdi.application.common.Actor;
import com.cdi.application.deployment.GetDeploymentQueryService;
import com.cdi.application.deployment.ListDeploymentsQueryService;
import com.cdi.application.deployment.RecordDeploymentHandler;
import com.cdi.application.deployment.RecordDeploymentOutcomeHandler;
import com.cdi.application.port.in.GetDeploymentQuery;
import com.cdi.application.port.in.ListDeploymentsQuery;
import com.cdi.application.port.in.RecordDeploymentCommand;
import com.cdi.application.port.in.RecordDeploymentOutcomeCommand;
import com.cdi.common.adapter.in.web.DevSecurityContext;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.adapter.in.web.DeploymentWebDtos.DeploymentDto;
import com.cdi.deployment.adapter.in.web.DeploymentWebDtos.RecordDeploymentRequest;
import com.cdi.deployment.adapter.in.web.DeploymentWebDtos.RecordDeploymentResponse;
import com.cdi.deployment.adapter.in.web.DeploymentWebDtos.RecordOutcomeRequest;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

  private final RecordDeploymentHandler recordDeploymentHandler;
  private final RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler;
  private final GetDeploymentQueryService getDeploymentQueryService;
  private final ListDeploymentsQueryService listDeploymentsQueryService;

  public DeploymentController(
      RecordDeploymentHandler recordDeploymentHandler,
      RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler,
      GetDeploymentQueryService getDeploymentQueryService,
      ListDeploymentsQueryService listDeploymentsQueryService) {
    this.recordDeploymentHandler = recordDeploymentHandler;
    this.recordDeploymentOutcomeHandler = recordDeploymentOutcomeHandler;
    this.getDeploymentQueryService = getDeploymentQueryService;
    this.listDeploymentsQueryService = listDeploymentsQueryService;
  }

  @PostMapping
  public ResponseEntity<RecordDeploymentResponse> recordDeployment(
      @RequestBody RecordDeploymentRequest request,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    Instant deployedAt = request.deployedAt() != null ? request.deployedAt() : Instant.now();
    DeploymentStatus status = request.status() != null ? request.status() : DeploymentStatus.IN_PROGRESS;

    RecordDeploymentCommand command = new RecordDeploymentCommand(
        tenantId,
        new ServiceId(request.serviceId()),
        request.commitSha(),
        request.environment(),
        status,
        request.externalDeploymentId(),
        deployedAt,
        actor);

    RecordDeploymentHandler.RecordDeploymentResult result = recordDeploymentHandler.handle(command);
    DeploymentDto dto = DeploymentDto.fromDomain(result.deployment());
    RecordDeploymentResponse body = new RecordDeploymentResponse(dto, result.created());

    HttpStatus httpStatus = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(httpStatus).body(body);
  }

  @PostMapping("/{id}/outcomes")
  public ResponseEntity<DeploymentDto> recordOutcome(
      @PathVariable("id") UUID id,
      @RequestBody RecordOutcomeRequest request,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    Instant recordedAt = request.recordedAt() != null ? request.recordedAt() : Instant.now();

    RecordDeploymentOutcomeCommand command = new RecordDeploymentOutcomeCommand(
        tenantId,
        new DeploymentId(id),
        request.outcome(),
        request.incidentReference(),
        recordedAt,
        actor);

    Deployment deployment = recordDeploymentOutcomeHandler.handle(command);
    return ResponseEntity.ok(DeploymentDto.fromDomain(deployment));
  }

  @GetMapping("/{id}")
  public ResponseEntity<DeploymentDto> getDeployment(
      @PathVariable("id") UUID id,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    GetDeploymentQuery query = new GetDeploymentQuery(tenantId, new DeploymentId(id), actor);
    Deployment deployment = getDeploymentQueryService.handle(query);
    return ResponseEntity.ok(DeploymentDto.fromDomain(deployment));
  }

  @GetMapping
  public ResponseEntity<List<DeploymentDto>> listDeployments(
      @RequestParam(value = "serviceId", required = false) UUID serviceId,
      @RequestParam(value = "environment", required = false) String environment,
      HttpServletRequest httpRequest) {
    TenantId tenantId = DevSecurityContext.extractTenantId(httpRequest);
    Actor actor = DevSecurityContext.extractActor(httpRequest);

    ServiceId svcId = serviceId != null ? new ServiceId(serviceId) : null;
    ListDeploymentsQuery query = new ListDeploymentsQuery(tenantId, svcId, environment, actor);

    List<Deployment> deployments = listDeploymentsQueryService.handle(query);
    List<DeploymentDto> dtos = deployments.stream().map(DeploymentDto::fromDomain).toList();
    return ResponseEntity.ok(dtos);
  }
}