package com.cdi.application.attribution;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.deployment.RecordDeploymentHandler;
import com.cdi.application.deployment.RecordDeploymentOutcomeHandler;
import com.cdi.application.port.in.GetAttributionSummaryQuery;
import com.cdi.application.port.in.GetDeploymentAttributionQuery;
import com.cdi.application.port.in.RecordDeploymentCommand;
import com.cdi.application.port.in.RecordDeploymentOutcomeCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionAttributionRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.ServiceRepository;
import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.AttributionSummary;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.deployment.domain.DeploymentStatus;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.organization.domain.Organization;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class DecisionAttributionPersistenceIntegrationTest {

  @Autowired
  private GetDeploymentAttributionQueryService getDeploymentAttributionQueryService;

  @Autowired
  private GetAttributionSummaryQueryService getAttributionSummaryQueryService;

  @Autowired
  private RecordDeploymentHandler recordDeploymentHandler;

  @Autowired
  private RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private ServiceRepository serviceRepository;

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private RiskAssessmentRepository riskAssessmentRepository;

  @Autowired
  private DecisionRecordRepository decisionRecordRepository;

  @Autowired
  private PolicyRepository policyRepository;

  @Autowired
  private DecisionAttributionRepository decisionAttributionRepository;

  private final Actor engineer = new Actor("eng-attr", Actor.Role.ENGINEER);
  private final Actor admin = new Actor("admin-attr", Actor.Role.TENANT_ADMIN);
  private final Actor worker = new Actor("worker-attr", Actor.Role.SYSTEM_WORKER);

  @Test
  void attributesSuccessfulDeploymentWithLowRiskAsAccurateLowRisk() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "payment-service");
    String commitSha = "sha-pay-1";

    // 1. Seed complete evaluation pipeline for commit
    seedAnalysisPipeline(tenantId, commitSha, RiskLevel.LOW, DecisionOutcome.APPROVE);

    // 2. Record Deployment & Outcome
    var depResult = recordDeploymentHandler.handle(new RecordDeploymentCommand(
        tenantId, service.getId(), commitSha, "production", DeploymentStatus.IN_PROGRESS,
        "deploy-pay-1", Instant.parse("2026-08-16T12:00:00Z"), worker));

    recordDeploymentOutcomeHandler.handle(new RecordDeploymentOutcomeCommand(
        tenantId, depResult.deployment().getId(), OutcomeType.SUCCESS, null,
        Instant.parse("2026-08-16T12:30:00Z"), worker));

    // 3. Query Attribution
    DecisionAttribution attribution = getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), engineer));

    assertNotNull(attribution);
    assertEquals(AttributionClassification.ACCURATE_LOW_RISK, attribution.getClassification());
    assertEquals(OutcomeType.SUCCESS, attribution.getDeploymentOutcome());
    assertEquals(RiskLevel.LOW, attribution.getPredictedRiskLevel().orElse(null));
    assertEquals(DecisionOutcome.APPROVE, attribution.getDecisionOutcome().orElse(null));

    // 4. Repeated query retrieves immutable record (does not duplicate)
    DecisionAttribution repeated = getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), admin));
    assertEquals(attribution.getId(), repeated.getId());
  }

  @Test
  void attributesIncidentWithLowRiskAsUnderestimatedRisk() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "auth-service");
    String commitSha = "sha-auth-underest";

    seedAnalysisPipeline(tenantId, commitSha, RiskLevel.LOW, DecisionOutcome.APPROVE);

    var depResult = recordDeploymentHandler.handle(new RecordDeploymentCommand(
        tenantId, service.getId(), commitSha, "production", DeploymentStatus.IN_PROGRESS,
        "deploy-auth-1", Instant.parse("2026-08-16T12:00:00Z"), worker));

    recordDeploymentOutcomeHandler.handle(new RecordDeploymentOutcomeCommand(
        tenantId, depResult.deployment().getId(), OutcomeType.INCIDENT, "INC-12345",
        Instant.parse("2026-08-16T12:30:00Z"), worker));

    DecisionAttribution attribution = getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), engineer));

    assertEquals(AttributionClassification.UNDERESTIMATED_RISK, attribution.getClassification());
    assertEquals(OutcomeType.INCIDENT, attribution.getDeploymentOutcome());
  }

  @Test
  void attributesDeploymentWithoutPriorAnalysisAsUnattributed() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "inventory-service");
    String commitSha = "sha-no-prior-analysis";

    var depResult = recordDeploymentHandler.handle(new RecordDeploymentCommand(
        tenantId, service.getId(), commitSha, "production", DeploymentStatus.IN_PROGRESS,
        "deploy-inv-1", Instant.parse("2026-08-16T12:00:00Z"), worker));

    recordDeploymentOutcomeHandler.handle(new RecordDeploymentOutcomeCommand(
        tenantId, depResult.deployment().getId(), OutcomeType.SUCCESS, null,
        Instant.parse("2026-08-16T12:30:00Z"), worker));

    DecisionAttribution attribution = getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), engineer));

    assertEquals(AttributionClassification.UNATTRIBUTED, attribution.getClassification());
    assertTrue(attribution.getAnalysisRunId().isEmpty());
    assertTrue(attribution.getRiskAssessmentId().isEmpty());
  }

  @Test
  void summaryAggregatesMetricsAccurately() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "metrics-service");

    // Seed 1: Accurate Low (SUCCESS + LOW)
    seedAndAttribute(tenantId, service, "sha-m1", RiskLevel.LOW, OutcomeType.SUCCESS);
    // Seed 2: Accurate High (FAILURE + HIGH)
    seedAndAttribute(tenantId, service, "sha-m2", RiskLevel.HIGH, OutcomeType.FAILURE);
    // Seed 3: Underestimated (INCIDENT + MEDIUM)
    seedAndAttribute(tenantId, service, "sha-m3", RiskLevel.MEDIUM, OutcomeType.INCIDENT);
    // Seed 4: Overestimated (SUCCESS + CRITICAL)
    seedAndAttribute(tenantId, service, "sha-m4", RiskLevel.CRITICAL, OutcomeType.SUCCESS);
    // Seed 5: Unattributed (No run + SUCCESS)
    seedUnattributed(tenantId, service, "sha-m5", OutcomeType.SUCCESS);

    AttributionSummary summary = getAttributionSummaryQueryService.handle(
        new GetAttributionSummaryQuery(tenantId, service.getId(), engineer));

    assertEquals(5, summary.totalAttributedDeployments());
    assertEquals(1, summary.accurateLowRiskCount());
    assertEquals(1, summary.accurateHighRiskCount());
    assertEquals(1, summary.underestimatedRiskCount());
    assertEquals(1, summary.overestimatedRiskCount());
    assertEquals(1, summary.unattributedCount());
    // accuracyRate = (1 accurateLow + 1 accurateHigh) / (4 evaluated) = 2/4 = 0.50
    assertEquals(0.50, summary.accuracyRate(), 0.001);
  }

  @Test
  void strictTenantIsolationEnforced() {
    TenantId tenantA = seedTenant();
    TenantId tenantB = seedTenant();

    Service serviceA = seedService(tenantA, "svc-a");
    Service serviceB = seedService(tenantB, "svc-b");

    seedAndAttribute(tenantA, serviceA, "sha-tenant-a", RiskLevel.LOW, OutcomeType.SUCCESS);

    AttributionSummary summaryB = getAttributionSummaryQueryService.handle(
        new GetAttributionSummaryQuery(tenantB, null, engineer));
    assertEquals(0, summaryB.totalAttributedDeployments(), "Tenant B must not see Tenant A attributions");
  }

  @Test
  void authorizationEnforcedForAttributionQueries() {
    TenantId tenantId = seedTenant();
    Service service = seedService(tenantId, "auth-check-svc");
    seedAndAttribute(tenantId, service, "sha-authz", RiskLevel.LOW, OutcomeType.SUCCESS);

    // SYSTEM_WORKER not allowed to query attribution (only ENGINEER and TENANT_ADMIN)
    ApplicationException ex = assertThrows(ApplicationException.class, () ->
        getAttributionSummaryQueryService.handle(new GetAttributionSummaryQuery(tenantId, service.getId(), worker)));
    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private void seedAndAttribute(
      TenantId tenantId, Service service, String commitSha, RiskLevel riskLevel, OutcomeType outcome) {
    seedAnalysisPipeline(tenantId, commitSha, riskLevel, DecisionOutcome.APPROVE);
    var depResult = recordDeploymentHandler.handle(new RecordDeploymentCommand(
        tenantId, service.getId(), commitSha, "production", DeploymentStatus.IN_PROGRESS,
        "dep-" + commitSha, Instant.now(), worker));
    recordDeploymentOutcomeHandler.handle(new RecordDeploymentOutcomeCommand(
        tenantId, depResult.deployment().getId(), outcome, null, Instant.now(), worker));
    getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), engineer));
  }

  private void seedUnattributed(TenantId tenantId, Service service, String commitSha, OutcomeType outcome) {
    var depResult = recordDeploymentHandler.handle(new RecordDeploymentCommand(
        tenantId, service.getId(), commitSha, "production", DeploymentStatus.IN_PROGRESS,
        "dep-" + commitSha, Instant.now(), worker));
    recordDeploymentOutcomeHandler.handle(new RecordDeploymentOutcomeCommand(
        tenantId, depResult.deployment().getId(), outcome, null, Instant.now(), worker));
    getDeploymentAttributionQueryService.handle(
        new GetDeploymentAttributionQuery(tenantId, depResult.deployment().getId(), engineer));
  }

  private void seedAnalysisPipeline(TenantId tenantId, String commitSha, RiskLevel riskLevel, DecisionOutcome decisionOutcome) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-" + commitSha,
        "Title " + commitSha, "Desc", "author", "feature", "main", commitSha, Instant.now());
    changeRepository.save(tenantId, change);

    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.now());
    run.start();
    run.complete(Instant.now());
    analysisRunRepository.save(tenantId, run);

    int scoreVal = switch (riskLevel) {
      case LOW -> 20;
      case MEDIUM -> 50;
      case HIGH -> 70;
      case CRITICAL -> 90;
    };

    RiskAssessment risk = new RiskAssessment(
        RiskAssessmentId.generate(), run.getId(),
        RiskScore.of(scoreVal), riskLevel,
        List.of(new RiskFactor(RiskFactorType.HIGH_SERVICE_CRITICALITY, scoreVal, "Risk signal", null)),
        EvidenceState.EVIDENCE_AVAILABLE, DeterministicRiskEngine.RULE_VERSION, Instant.now());
    riskAssessmentRepository.save(tenantId, risk);

    Optional<Policy> existingPolicy = policyRepository.findActiveByTenant(tenantId);
    Policy policy;
    if (existingPolicy.isPresent()) {
      policy = existingPolicy.get();
    } else {
      policy = new Policy(
          PolicyId.generate(), tenantId, "Default Policy", "1.0",
          PolicyStatus.ACTIVE, new PolicyVersion("1.0"),
          List.of(new PolicyRule("rule-1", Set.of(CriticalityTier.TIER_0), Set.of(RiskLevel.HIGH), Set.of(), DecisionOutcome.REVIEW_REQUIRED, List.of(), "Guard")),
          Instant.now());
      policy = policyRepository.save(policy);
    }

    DecisionRecord decision = DecisionRecord.builder()
        .id(DecisionId.generate())
        .tenantId(tenantId)
        .analysisRunId(run.getId())
        .riskAssessmentId(risk.getId())
        .policyId(policy.getId())
        .policyVersion(policy.getVersion().value())
        .outcome(decisionOutcome)
        .reasons(List.of(new DecisionReason("Policy rule applied", "rule-1", List.of())))
        .requiredActions(List.of())
        .generatedAt(Instant.now())
        .build();
    decisionRecordRepository.save(tenantId, decision);
  }

  private TenantId seedTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(tenantId, "Test Org " + tenantId.value(), Instant.now()));
    return tenantId;
  }

  private Service seedService(TenantId tenantId, String name) {
    Service service = new Service(
        ServiceId.generate(), tenantId, name, CriticalityTier.TIER_1, "team-core", Instant.now());
    return serviceRepository.save(service);
  }
}