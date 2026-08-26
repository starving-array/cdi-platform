package com.cdi.deployment.adapter.in.web;

import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.deployment.GetDeploymentQueryService;
import com.cdi.application.deployment.ListDeploymentsQueryService;
import com.cdi.application.deployment.RecordDeploymentHandler;
import com.cdi.application.deployment.RecordDeploymentOutcomeHandler;
import com.cdi.common.adapter.in.web.RestApiExceptionHandler;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.deployment.domain.OutcomeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeploymentController.class)
@Import(RestApiExceptionHandler.class)
class DeploymentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private RecordDeploymentHandler recordDeploymentHandler;

  @MockBean
  private RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler;

  @MockBean
  private GetDeploymentQueryService getDeploymentQueryService;

  @MockBean
  private ListDeploymentsQueryService listDeploymentsQueryService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();
  private final UUID deploymentId = UUID.randomUUID();

  @Test
  void recordDeploymentReturns201CreatedWhenNew() throws Exception {
    Deployment deployment = new Deployment(
        new DeploymentId(deploymentId),
        new TenantId(tenantId),
        new ServiceId(serviceId),
        "sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-1",
        Instant.parse("2026-08-16T12:00:00Z"),
        Instant.parse("2026-08-16T12:00:00Z"));

    when(recordDeploymentHandler.handle(any()))
        .thenReturn(new RecordDeploymentHandler.RecordDeploymentResult(deployment, true));

    mockMvc.perform(post("/api/v1/deployments")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "worker-1")
            .header("X-Actor-Role", "SYSTEM_WORKER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "serviceId": "%s",
                  "commitSha": "sha-123",
                  "environment": "production",
                  "externalDeploymentId": "ext-1"
                }
                """.formatted(serviceId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(true))
        .andExpect(jsonPath("$.deployment.id").value(deploymentId.toString()))
        .andExpect(jsonPath("$.deployment.commitSha").value("sha-123"));
  }

  @Test
  void recordDeploymentReturns200OkOnIdempotentReplay() throws Exception {
    Deployment deployment = new Deployment(
        new DeploymentId(deploymentId),
        new TenantId(tenantId),
        new ServiceId(serviceId),
        "sha-123",
        "production",
        DeploymentStatus.IN_PROGRESS,
        "ext-1",
        Instant.parse("2026-08-16T12:00:00Z"),
        Instant.parse("2026-08-16T12:00:00Z"));

    when(recordDeploymentHandler.handle(any()))
        .thenReturn(new RecordDeploymentHandler.RecordDeploymentResult(deployment, false));

    mockMvc.perform(post("/api/v1/deployments")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "worker-1")
            .header("X-Actor-Role", "SYSTEM_WORKER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "serviceId": "%s",
                  "commitSha": "sha-123",
                  "environment": "production",
                  "externalDeploymentId": "ext-1"
                }
                """.formatted(serviceId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(false))
        .andExpect(jsonPath("$.deployment.id").value(deploymentId.toString()));
  }

  @Test
  void recordOutcomeReturns200WithUpdatedOutcome() throws Exception {
    Deployment deployment = new Deployment(
        new DeploymentId(deploymentId),
        new TenantId(tenantId),
        new ServiceId(serviceId),
        "sha-123",
        "production",
        DeploymentStatus.SUCCESSFUL,
        "ext-1",
        Instant.parse("2026-08-16T12:00:00Z"),
        Instant.parse("2026-08-16T12:00:00Z"));
    deployment.recordOutcome(OutcomeType.SUCCESS, null, Instant.now(), Instant.now());

    when(recordDeploymentOutcomeHandler.handle(any())).thenReturn(deployment);

    mockMvc.perform(post("/api/v1/deployments/{id}/outcomes", deploymentId)
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "worker-1")
            .header("X-Actor-Role", "SYSTEM_WORKER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "outcome": "SUCCESS"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESSFUL"))
        .andExpect(jsonPath("$.outcome.outcome").value("SUCCESS"));
  }

  @Test
  void getDeploymentReturns404WhenNotFound() throws Exception {
    when(getDeploymentQueryService.handle(any()))
        .thenThrow(new ApplicationException(ApplicationError.DEPLOYMENT_NOT_FOUND, "Deployment not found"));

    mockMvc.perform(get("/api/v1/deployments/{id}", deploymentId)
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "eng-1")
            .header("X-Actor-Role", "ENGINEER"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEPLOYMENT_NOT_FOUND"));
  }
}