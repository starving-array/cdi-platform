package com.cdi.decision.adapter.out.persistence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code decision_record}/{@code decision_reason}
 * table adapters against a real PostgreSQL Testcontainer. Scenarios mirror the
 * UC-05 policy-evaluation persistence contract (application-layer.md §6/§8/§10,
 * data-model.md §E).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class DecisionRecordPersistenceIntegrationTest {

  @Autowired
  private DecisionRecordRepository adapter;

  @Autowired
  private DecisionRecordJpaRepository decisionRecordJpaRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private RiskAssessmentRepository riskAssessmentRepository;

  private AnalysisRunId requireAssessment(TenantId tenant) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-42",
        "Fix", "", "alice", "feature", "main", "sha9", Instant.now());
    change = changeRepository.save(tenant, change);
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot("sha9", "feature"), Instant.now());
    AnalysisRunId runId = analysisRunRepository.save(tenant, run).getId();
    RiskAssessment assessment = new RiskAssessment(
        RiskAssessmentId.generate(), runId,
        RiskScore.of(65), RiskLevel.HIGH, List.of(),
        EvidenceState.EVIDENCE_AVAILABLE, DeterministicRiskEngine.RULE_VERSION,
        Instant.parse("2026-08-15T12:00:00Z"));
    riskAssessmentRepository.save(tenant, assessment);
    return runId;
  }

  @Test
  void savePersistsDecisionWithReasonsActionsAndEvidenceReferences() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAssessment(tenant);
    EvidenceId incident = EvidenceId.generate();
    PolicyId policyId = PolicyId.generate();
    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenant)
        .analysisRunId(runId)
        .riskAssessmentId(riskAssessmentRepository
            .findByAnalysisRunId(tenant, runId).orElseThrow().getId())
        .policyId(policyId)
        .policyVersion("v2.1.0")
        .outcome(DecisionOutcome.BLOCK)
        .reasons(List.of(
            new DecisionReason("Tier-0 + HIGH must be blocked.", "BLOCK_T0_HIGH",
                List.of(incident))))
        .requiredActions(List.of(RequiredAction.SECURITY_REVIEW))
        .generatedAt(Instant.parse("2026-08-15T12:00:00Z").truncatedTo(ChronoUnit.MICROS))
        .build();

    adapter.save(tenant, decision);

    DecisionRecordEntity row = decisionRecordJpaRepository
        .findById(decision.getId().value()).orElseThrow();
    assertEquals(tenant.value(), row.getTenantId());
    assertEquals(runId.value(), row.getAnalysisRunId());
    assertEquals(policyId.value(), row.getPolicyId());
    assertEquals("v2.1.0", row.getPolicyVersion());
    assertEquals(DecisionOutcome.BLOCK.name(), row.getOutcome());
    assertEquals(RequiredAction.SECURITY_REVIEW.name(), row.getRequiredActions());
    assertEquals(1, row.getReasons().size());
    assertEquals("BLOCK_T0_HIGH", row.getReasons().get(0).getRuleId());
    assertEquals(incident.value().toString(), row.getReasons().get(0).getEvidenceReferenceIds());

    Optional<DecisionRecord> reloaded = adapter.findByAnalysisRunId(tenant, runId);
    assertTrue(reloaded.isPresent());
    assertEquals(DecisionOutcome.BLOCK, reloaded.get().getOutcome());
    assertEquals("v2.1.0", reloaded.get().getPolicyVersion());
    assertEquals(List.of(RequiredAction.SECURITY_REVIEW), reloaded.get().getRequiredActions());
    assertEquals(incident, reloaded.get().getReasons().get(0).evidenceReferences().get(0));
  }

  @Test
  void tenantScopedLookupDoesNotLeakAcrossTenants() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAssessment(tenant);
    adapter.save(tenant, minimalDecision(tenant, runId));

    TenantId otherTenant = TenantId.generate();
    assertTrue(adapter.findByAnalysisRunId(otherTenant, runId).isEmpty());
  }

  @Test
  void tenantScopedForeignKeyRejectsDecisionForUnknownRun() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId unknownRunId = AnalysisRunId.generate();
    RiskAssessmentId unknownRiskId = RiskAssessmentId.generate();

    DecisionRecord decision = DecisionRecord.builder()
        .tenantId(tenant)
        .analysisRunId(unknownRunId)
        .riskAssessmentId(unknownRiskId)
        .policyId(PolicyId.generate())
        .policyVersion("v1")
        .outcome(DecisionOutcome.REVIEW_REQUIRED)
        .reasons(List.of(new DecisionReason("No rule matched.", "DEFAULT_FALLBACK", List.of())))
        .requiredActions(List.of())
        .generatedAt(Instant.parse("2026-08-15T12:00:00Z"))
        .build();

    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, decision));
  }

  @Test
  void onlyOneDecisionPerRunIsEnforcedOnSecondSaveForSameRun() {
    TenantId tenant = TenantId.generate();
    AnalysisRunId runId = requireAssessment(tenant);

    adapter.save(tenant, minimalDecision(tenant, runId));

    DecisionRecord duplicate = minimalDecision(tenant, runId);
    assertThrows(DataIntegrityViolationException.class,
        () -> adapter.save(tenant, duplicate));
  }

  private DecisionRecord minimalDecision(TenantId tenant, AnalysisRunId runId) {
    return DecisionRecord.builder()
        .tenantId(tenant)
        .analysisRunId(runId)
        .riskAssessmentId(riskAssessmentRepository
            .findByAnalysisRunId(tenant, runId).orElseThrow().getId())
        .policyId(PolicyId.generate())
        .policyVersion("v1")
        .outcome(DecisionOutcome.APPROVE)
        .reasons(List.of(new DecisionReason("Approve all.", "APPROVE_ALL", List.of())))
        .requiredActions(List.of())
        .generatedAt(Instant.parse("2026-08-15T12:00:00Z"))
        .build();
  }
}
