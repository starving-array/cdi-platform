package com.cdi.risk.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
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

/**
 * Integration tests for the {@code risk_assessment}/{@code risk_factor}
 * table adapters against a real PostgreSQL Testcontainer. Scenarios mirror the
 * UC-03 persistence contract (application-layer.md §6/§8, data-model.md §D).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class RiskAssessmentPersistenceIntegrationTest {

  @Autowired
  private RiskAssessmentRepository adapter;

  @Autowired
  private RiskAssessmentJpaRepository riskAssessmentJpaRepository;

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
  void savePersistsAssessmentWithFactorsAndEvidenceReferences() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);
    EvidenceId incident = EvidenceId.generate();
    Instant calculatedAt = Instant.parse("2026-08-15T12:00:00Z")
        .truncatedTo(ChronoUnit.MICROS);
    RiskAssessment assessment = new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        RiskScore.of(65), RiskLevel.HIGH,
        List.of(
            new RiskFactor(RiskFactorType.HIGH_SERVICE_CRITICALITY, 30,
                "Change affects a Tier-0 service.", List.of()),
            new RiskFactor(RiskFactorType.HISTORICAL_INCIDENT_MATCH, 20,
                "Matched 1 similar historical incident.", List.of(incident))),
        EvidenceState.EVIDENCE_AVAILABLE,
        DeterministicRiskEngine.RULE_VERSION,
        calculatedAt);

    adapter.save(tenant, assessment);

    RiskAssessmentEntity row = riskAssessmentJpaRepository
        .findById(assessment.getId().value()).orElseThrow();
    assertEquals(tenant.value(), row.getTenantId());
    assertEquals(runId.value(), row.getAnalysisRunId());
    assertEquals(65, row.getRiskScore());
    assertEquals(RiskLevel.HIGH.name(), row.getRiskLevel());
    assertEquals(EvidenceState.EVIDENCE_AVAILABLE.name(), row.getEvidenceState());
    assertEquals(2, row.getFactors().size());
    assertEquals(incident.value().toString(),
        row.getFactors().get(1).getEvidenceReferenceIds());
  }

  @Test
  void tenantScopedForeignKeyRejectsAssessmentForUnknownRun() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId unknownRunId = AnalysisRunId.generate();
    RiskAssessment assessment = minimalAssessment(unknownRunId);

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, assessment));
  }

  @Test
  void onlyOneAssessmentPerRunIsEnforcedOnSecondSaveForSameRun() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAnalysisRun(tenant);

    adapter.save(tenant, minimalAssessment(runId));

    RiskAssessment duplicate = new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        RiskScore.of(10), RiskLevel.LOW, List.of(),
        EvidenceState.NO_RELEVANT_EVIDENCE,
        DeterministicRiskEngine.RULE_VERSION, Instant.now());

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, duplicate));
  }

  private RiskAssessment minimalAssessment(AnalysisRunId runId) {
    return new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        RiskScore.of(10), RiskLevel.LOW, List.of(),
        EvidenceState.NO_RELEVANT_EVIDENCE,
        DeterministicRiskEngine.RULE_VERSION,
        Instant.parse("2026-08-15T12:00:00Z"));
  }
}