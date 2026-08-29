package com.cdi.change.adapter.in.web;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.change.ChangeAnalysisView;
import com.cdi.application.change.GetChangeQueryService;
import com.cdi.application.change.ListChangesQueryService;
import com.cdi.application.change.ProposeChangeHandler;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.change.domain.Change;
import com.cdi.common.adapter.in.web.DevCorsConfiguration;
import com.cdi.common.adapter.in.web.RestApiExceptionHandler;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChangeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({RestApiExceptionHandler.class, DevCorsConfiguration.class})
class ChangeControllerTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ListChangesQueryService listChangesQueryService;

  @MockBean
  private GetChangeQueryService getChangeQueryService;

  @MockBean
  private ProposeChangeHandler proposeChangeHandler;

  @Test
  void engineerCanListChangesSuccessfully() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Change c1 = new Change(
        ChangeId.generate(), new RepositoryId(repoId), "PR-1", "Fix payment logic",
        "Desc", "alice", "feature", "main", "sha-1", NOW);

    when(listChangesQueryService.handle(any(), eq(new com.cdi.common.domain.id.TenantId(tenantId)), any()))
        .thenReturn(List.of(c1));

    mockMvc.perform(get("/api/v1/changes")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "eng-1")
            .header("X-Actor-Role", "ENGINEER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value("Fix payment logic"))
        .andExpect(jsonPath("$[0].status").value("OPEN"));
  }

  @Test
  void tenantAdminCanListChangesSuccessfully() throws Exception {
    UUID tenantId = UUID.randomUUID();
    when(listChangesQueryService.handle(any(), eq(new com.cdi.common.domain.id.TenantId(tenantId)), any()))
        .thenReturn(List.of());

    mockMvc.perform(get("/api/v1/changes")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "admin-1")
            .header("X-Actor-Role", "TENANT_ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void missingTenantHeaderReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/changes"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void unauthorizedRoleRejected() throws Exception {
    UUID tenantId = UUID.randomUUID();
    when(listChangesQueryService.handle(any(), any(), any()))
        .thenThrow(new ApplicationException(ApplicationError.UNAUTHORIZED));

    mockMvc.perform(get("/api/v1/changes")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Role", "SYSTEM_WORKER"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void invalidRepositoryFilterReturnsRepositoryNotFound() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID missingRepoId = UUID.randomUUID();
    when(listChangesQueryService.handle(any(), any(), any()))
        .thenThrow(new ApplicationException(ApplicationError.REPOSITORY_NOT_FOUND));

    mockMvc.perform(get("/api/v1/changes")
            .header("X-Tenant-Id", tenantId.toString())
            .param("repositoryId", missingRepoId.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_FOUND"));
  }

  @Test
  void getChangeReturnsDetailResponse() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Change change = new Change(
        ChangeId.generate(), new RepositoryId(repoId), "PR-10", "Refactor auth",
        "Desc", "bob", "auth-branch", "main", "sha-10", NOW);
    RiskAssessment risk = new RiskAssessment(
        com.cdi.common.domain.id.RiskAssessmentId.generate(),
        com.cdi.common.domain.id.AnalysisRunId.generate(),
        RiskScore.of(42), RiskLevel.MEDIUM, List.of(), EvidenceState.EVIDENCE_AVAILABLE, "v1", NOW);
    DecisionRecord decision = DecisionRecord.builder()
        .id(com.cdi.common.domain.id.DecisionId.generate())
        .tenantId(new com.cdi.common.domain.id.TenantId(tenantId))
        .analysisRunId(com.cdi.common.domain.id.AnalysisRunId.generate())
        .riskAssessmentId(com.cdi.common.domain.id.RiskAssessmentId.generate())
        .policyId(com.cdi.common.domain.id.PolicyId.generate())
        .policyVersion("v1")
        .outcome(DecisionOutcome.APPROVE)
        .reasons(List.of())
        .requiredActions(List.of())
        .generatedAt(NOW)
        .build();

    ChangeAnalysisView view = new ChangeAnalysisView(
        change, List.of(), risk, decision);

    when(getChangeQueryService.handle(any(), any(), any())).thenReturn(view);

    mockMvc.perform(get("/api/v1/changes/" + change.getId().value())
            .header("X-Tenant-Id", tenantId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.change.title").value("Refactor auth"))
        .andExpect(jsonPath("$.latestRisk.overallScore").value(42))
        .andExpect(jsonPath("$.latestRisk.riskLevel").value("MEDIUM"))
        .andExpect(jsonPath("$.latestDecision.outcome").value("APPROVE"));
  }

  @Test
  void getChangeNotFoundReturns404() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID missingId = UUID.randomUUID();
    when(getChangeQueryService.handle(any(), any(), any()))
        .thenThrow(new ApplicationException(ApplicationError.CHANGE_NOT_FOUND));

    mockMvc.perform(get("/api/v1/changes/" + missingId)
            .header("X-Tenant-Id", tenantId.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CHANGE_NOT_FOUND"));
  }

  @Test
  void corsAllowsDevFrontendOrigin() throws Exception {
    mockMvc.perform(options("/api/v1/changes")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void engineerProposeChangeReturns201Created() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    AnalysisRunId runId = AnalysisRunId.generate();

    when(proposeChangeHandler.handle(any()))
        .thenReturn(new IdempotentCommandResult(runId, true));

    mockMvc.perform(post("/api/v1/changes/propose")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "alice")
            .header("X-Actor-Role", "ENGINEER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "repositoryId": "%s",
                  "providerChangeId": "PR-100",
                  "commitSha": "abc123sha",
                  "branch": "feature/auth",
                  "title": "Add auth",
                  "description": "Desc",
                  "author": "alice"
                }
                """.formatted(repoId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.analysisRunId").value(runId.value().toString()))
        .andExpect(jsonPath("$.created").value(true));
  }

  @Test
  void duplicateProposeChangeReturns200Ok() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    AnalysisRunId runId = AnalysisRunId.generate();

    when(proposeChangeHandler.handle(any()))
        .thenReturn(new IdempotentCommandResult(runId, false));

    mockMvc.perform(post("/api/v1/changes/propose")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "alice")
            .header("X-Actor-Role", "ENGINEER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "repositoryId": "%s",
                  "providerChangeId": "PR-100",
                  "commitSha": "abc123sha"
                }
                """.formatted(repoId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisRunId").value(runId.value().toString()))
        .andExpect(jsonPath("$.created").value(false));
  }

  @Test
  void systemWorkerCanProposeChangeReturns201Created() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    AnalysisRunId runId = AnalysisRunId.generate();

    when(proposeChangeHandler.handle(any()))
        .thenReturn(new IdempotentCommandResult(runId, true));

    mockMvc.perform(post("/api/v1/changes/propose")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "ci-worker")
            .header("X-Actor-Role", "SYSTEM_WORKER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "repositoryId": "%s",
                  "providerChangeId": "PR-100",
                  "commitSha": "abc123sha"
                }
                """.formatted(repoId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.analysisRunId").value(runId.value().toString()))
        .andExpect(jsonPath("$.created").value(true));
  }

  @Test
  void tenantAdminProposeChangeReturns403Forbidden() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();

    when(proposeChangeHandler.handle(any()))
        .thenThrow(new ApplicationException(ApplicationError.UNAUTHORIZED));

    mockMvc.perform(post("/api/v1/changes/propose")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "admin")
            .header("X-Actor-Role", "TENANT_ADMIN")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "repositoryId": "%s",
                  "providerChangeId": "PR-100",
                  "commitSha": "abc123sha"
                }
                """.formatted(repoId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void missingRequiredFieldsReturns400BadRequest() throws Exception {
    UUID tenantId = UUID.randomUUID();

    mockMvc.perform(post("/api/v1/changes/propose")
            .header("X-Tenant-Id", tenantId.toString())
            .header("X-Actor-Id", "alice")
            .header("X-Actor-Role", "ENGINEER")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "commitSha": "abc123sha"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }
}