package com.cdi.investigation.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.port.out.AgentInvestigationRepository;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.investigation.domain.AgentInvestigation;
import com.cdi.investigation.domain.EvidenceCitation;
import com.cdi.investigation.domain.InvestigationFinding;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code agent_investigation}/{@code
 * investigation_finding} table adapters against a real PostgreSQL Testcontainer.
 * Mirrors the UC-04 persistence contract (application-layer.md §6/§8,
 * data-model.md §D/§F).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class AgentInvestigationPersistenceIntegrationTest {

  @Autowired
  private AgentInvestigationRepository adapter;

  @Autowired
  private AgentInvestigationJpaRepository agentInvestigationJpaRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private ChangeRepository changeRepository;

  private AnalysisRunId requireAnalysisRun(TenantId tenant) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());
    change = changeRepository.save(tenant, change);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot("sha9", "feature"), Instant.now());
    return analysisRunRepository.save(tenant, run).getId();
  }

  @Test
  void savePersistsInvestigationWithFindingsAndCitations() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);
    ChangeId changeId = analysisRunRepository.findById(runId)
        .orElseThrow().run().getChangeId();
    Instant createdAt = Instant.parse("2026-08-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS);
    EvidenceId incident = EvidenceId.generate();

    AgentInvestigation investigation = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, RiskAssessmentId.generate(), createdAt);
    investigation.start();
    investigation.complete(List.of(
        new InvestigationFinding("Payment path risk",
            "A past incident touched this path.",
            List.of(new EvidenceCitation(incident, "INC-100 snippet")))),
        createdAt.plusSeconds(30));

    adapter.save(tenant, investigation);

    AgentInvestigationEntity row = agentInvestigationJpaRepository
        .findById(investigation.getId().value()).orElseThrow();
    assertEquals(tenant.value(), row.getTenantId());
    assertEquals(runId.value(), row.getAnalysisRunId());
    assertEquals(AgentInvestigation.Status.COMPLETED.name(), row.getStatus());
    assertEquals(1, row.getFindings().size());
    assertEquals(incident.value().toString(), row.getFindings().get(0).getEvidenceCitationIds());
  }

  @Test
  void failedInvestigationPersistsFailureState() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);
    ChangeId changeId = analysisRunRepository.findById(runId)
        .orElseThrow().run().getChangeId();
    Instant createdAt = Instant.parse("2026-08-15T12:00:00Z");

    AgentInvestigation investigation = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, RiskAssessmentId.generate(), createdAt);
    investigation.start();
    investigation.fail(new com.cdi.investigation.domain.InvestigationFailure(
        com.cdi.investigation.domain.InvestigationFailure.FailureCategory.AGENT_UNAVAILABLE,
        "agent-unavailable", createdAt.plusSeconds(10)));

    adapter.save(tenant, investigation);

    AgentInvestigationEntity row = agentInvestigationJpaRepository
        .findById(investigation.getId().value()).orElseThrow();
    assertEquals(AgentInvestigation.Status.FAILED.name(), row.getStatus());
    assertEquals(
        com.cdi.investigation.domain.InvestigationFailure.FailureCategory.AGENT_UNAVAILABLE.name(),
        row.getFailureCategory());
    assertEquals("agent-unavailable", row.getFailureCode());
  }

  @Test
  void onlyOneInvestigationPerRunIsEnforcedOnSecondSave() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);
    ChangeId changeId = analysisRunRepository.findById(runId)
        .orElseThrow().run().getChangeId();
    Instant createdAt = Instant.parse("2026-08-15T12:00:00Z");

    AgentInvestigation first = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, RiskAssessmentId.generate(), createdAt);
    first.start();
    first.complete(List.of(), createdAt.plusSeconds(5));
    adapter.save(tenant, first);

    AgentInvestigation duplicate = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, RiskAssessmentId.generate(), createdAt);
    duplicate.start();

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, duplicate));
  }

  @Test
  void findByAnalysisRunIdResolvesSavedInvestigation() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);
    ChangeId changeId = analysisRunRepository.findById(runId)
        .orElseThrow().run().getChangeId();
    Instant createdAt = Instant.parse("2026-08-15T12:00:00Z");

    AgentInvestigation investigation = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, RiskAssessmentId.generate(), createdAt);
    investigation.start();
    investigation.complete(List.of(), createdAt.plusSeconds(5));
    adapter.save(tenant, investigation);

    assertTrue(adapter.findByAnalysisRunId(tenant, runId).isPresent());
  }

  @Test
  void tenantScopedForeignKeyRejectsInvestigationForUnknownRun() {
    TenantId tenant = TenantId.generate();
    AgentInvestigation investigation = new AgentInvestigation(
        InvestigationId.generate(), AnalysisRunId.generate(), ChangeId.generate(),
        RiskAssessmentId.generate(), Instant.now());
    investigation.start();

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, investigation));
  }
}
