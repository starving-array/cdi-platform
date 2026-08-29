package com.cdi.attribution.adapter.in.web;

import com.cdi.application.attribution.GetAttributionSummaryQueryService;
import com.cdi.application.attribution.GetDeploymentAttributionQueryService;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.common.adapter.in.web.RestApiExceptionHandler;
import com.cdi.common.domain.id.AttributionId;
import com.cdi.common.domain.id.DeploymentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.risk.domain.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttributionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RestApiExceptionHandler.class)
class AttributionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private GetDeploymentAttributionQueryService getDeploymentAttributionQueryService;

  @MockBean
  private GetAttributionSummaryQueryService getAttributionSummaryQueryService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID deploymentId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();

  @Test
  void getDeploymentAttributionReturns200WithAttributionDto() throws Exception {
    DecisionAttribution attribution = new DecisionAttribution(
        AttributionId.generate(),
        new TenantId(tenantId),
        new DeploymentId(deploymentId),
        new ServiceId(serviceId),
        null,
        null,
        null,
        AttributionClassification.ACCURATE_LOW_RISK,
        OutcomeType.SUCCESS,
        RiskLevel.LOW,
        DecisionOutcome.APPROVE,
        false,
        Instant.parse("2026-08-16T12:00:00Z"),
        Instant.parse("2026-08-16T12:00:00Z"));

    when(getDeploymentAttributionQueryService.handle(any())).thenReturn(attribution);

    mockMvc.perform(get("/api/v1/attribution/{deploymentId}", deploymentId)
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "eng-1")
            .header("X-Actor-Role", "ENGINEER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentId").value(deploymentId.toString()))
        .andExpect(jsonPath("$.classification").value("ACCURATE_LOW_RISK"))
        .andExpect(jsonPath("$.deploymentOutcome").value("SUCCESS"))
        .andExpect(jsonPath("$.predictedRiskLevel").value("LOW"));
  }

  @Test
  void getDeploymentAttributionReturns404WhenNotFound() throws Exception {
    when(getDeploymentAttributionQueryService.handle(any()))
        .thenThrow(new ApplicationException(ApplicationError.ATTRIBUTION_NOT_FOUND, "Not found"));

    mockMvc.perform(get("/api/v1/attribution/{deploymentId}", deploymentId)
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "eng-1")
            .header("X-Actor-Role", "ENGINEER"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ATTRIBUTION_NOT_FOUND"));
  }

  @Test
  void getSummaryReturns200WithSummaryDto() throws Exception {
    AttributionSummary summary = new AttributionSummary(
        new TenantId(tenantId),
        new ServiceId(serviceId),
        10, 5, 2, 1, 1, 1, 0.777);

    when(getAttributionSummaryQueryService.handle(any())).thenReturn(summary);

    mockMvc.perform(get("/api/v1/attribution/summary")
            .param("serviceId", serviceId.toString())
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "admin-1")
            .header("X-Actor-Role", "TENANT_ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAttributedDeployments").value(10))
        .andExpect(jsonPath("$.accurateLowRiskCount").value(5))
        .andExpect(jsonPath("$.accuracyRate").value(0.777));
  }

  @Test
  void systemWorkerRoleIsForbiddenOnAttributionEndpoints() throws Exception {
    when(getAttributionSummaryQueryService.handle(any()))
        .thenThrow(new ApplicationException(ApplicationError.UNAUTHORIZED, "Unauthorized"));

    mockMvc.perform(get("/api/v1/attribution/summary")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "worker-1")
            .header("X-Actor-Role", "SYSTEM_WORKER"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}