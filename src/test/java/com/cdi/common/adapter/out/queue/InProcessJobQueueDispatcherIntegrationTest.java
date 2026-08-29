package com.cdi.common.adapter.out.queue;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.change.ProposeChangeHandler;
import com.cdi.application.port.in.ProposeChangeCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.organization.domain.Organization;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.repository.domain.Repository;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class InProcessJobQueueDispatcherIntegrationTest {

  @Autowired
  private ProposeChangeHandler proposeChangeHandler;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private RiskAssessmentRepository riskAssessmentRepository;

  @Autowired
  private DecisionRecordRepository decisionRecordRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private RepositoryRepository repositoryRepository;

  @Autowired
  private PolicyRepository policyRepository;

  @Autowired
  private com.cdi.application.port.out.ChangeRepository changeRepository;

  @Autowired
  private com.cdi.application.port.out.JobQueuePort jobQueuePort;

  @Test
  void testPR103Scenario_MissingPolicyFailsAndRecovers() {
    TenantId tenantId = seedTenant();
    RepositoryId repoId = seedRepository(tenantId, "repo-pr103-scenario");

    ProposeChangeCommand command = new ProposeChangeCommand(
        tenantId,
        repoId,
        "PR-103",
        "sha-dispatch-103",
        "main",
        "Add missing policy",
        "Fails first, succeeds later",
        "alice",
        new Actor("alice", Actor.Role.ENGINEER),
        new IdempotencyKey("dispatch-test-103-" + UUID.randomUUID()));

    IdempotentCommandResult result = proposeChangeHandler.handle(command);
    assertTrue(result.created());
    AnalysisRunId runId = result.analysisRunId();

    // Await transition from QUEUED to FAILED (EvaluatePolicy fails due to missing policy)
    await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
      var contextOpt = analysisRunRepository.findById(runId);
      assertTrue(contextOpt.isPresent());
      assertEquals(AnalysisRun.Status.FAILED, contextOpt.get().run().getStatus());
    });

    // Run must NOT be COMPLETED
    assertEquals(AnalysisRun.Status.FAILED, analysisRunRepository.findById(runId).get().run().getStatus());

    // Decision record does NOT exist
    assertTrue(decisionRecordRepository.findByAnalysisRunId(tenantId, runId).isEmpty());

    // Create and activate policy
    seedActivePolicy(tenantId);

    // Retry/replay the failed policy stage via RequestAnalysisHandler
    com.cdi.application.analysis.RequestAnalysisHandler requestAnalysisHandler = new com.cdi.application.analysis.RequestAnalysisHandler(changeRepository, analysisRunRepository, jobQueuePort);
    com.cdi.common.domain.id.ChangeId changeId = analysisRunRepository.findById(runId).get().run().getChangeId();
    com.cdi.application.port.in.RequestAnalysisCommand retryCommand = new com.cdi.application.port.in.RequestAnalysisCommand(
        tenantId, changeId, "sha-dispatch-103", new Actor("alice", Actor.Role.ENGINEER), new IdempotencyKey("retry-103-" + UUID.randomUUID()));
    
    IdempotentCommandResult retryResult = requestAnalysisHandler.handle(retryCommand);
    assertEquals(runId, retryResult.analysisRunId());

    // Await transition to COMPLETED
    await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
      var contextOpt = analysisRunRepository.findById(runId);
      assertTrue(contextOpt.isPresent());
      assertEquals(AnalysisRun.Status.COMPLETED, contextOpt.get().run().getStatus());
    });

    // Verify DecisionRecord was generated and persisted
    Optional<DecisionRecord> decisionOpt = decisionRecordRepository.findByAnalysisRunId(tenantId, runId);
    assertTrue(decisionOpt.isPresent());
    assertEquals(DecisionOutcome.APPROVE, decisionOpt.get().getOutcome());
  }

  @Test
  void proposeChangeExecutesThroughDispatcherToCompletion() {
    TenantId tenantId = seedTenant();
    RepositoryId repoId = seedRepository(tenantId, "repo-beta-dispatcher");
    seedActivePolicy(tenantId);

    ProposeChangeCommand command = new ProposeChangeCommand(
        tenantId,
        repoId,
        "PR-DISPATCH-" + UUID.randomUUID(),
        "sha-dispatch-101",
        "main",
        "Add health check endpoint",
        "Adds actuator health check",
        "alice",
        new Actor("alice", Actor.Role.ENGINEER),
        new IdempotencyKey("dispatch-test-" + UUID.randomUUID()));

    IdempotentCommandResult result = proposeChangeHandler.handle(command);
    assertTrue(result.created());
    AnalysisRunId runId = result.analysisRunId();

    // The in-process dispatcher processes AnalyzeChangeCommand -> EvaluatePolicyCommand -> GenerateDecisionCommand
    // Await transition from QUEUED to COMPLETED
    await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
      var contextOpt = analysisRunRepository.findById(runId);
      assertTrue(contextOpt.isPresent());
      AnalysisRun run = contextOpt.get().run();
      assertEquals(AnalysisRun.Status.COMPLETED, run.getStatus());
    });

    // Verify RiskAssessment was generated and persisted
    Optional<RiskAssessment> riskOpt = riskAssessmentRepository.findByAnalysisRunId(tenantId, runId);
    assertTrue(riskOpt.isPresent());
    assertNotNull(riskOpt.get().getScore());

    // Verify DecisionRecord was generated and persisted
    Optional<DecisionRecord> decisionOpt = decisionRecordRepository.findByAnalysisRunId(tenantId, runId);
    assertTrue(decisionOpt.isPresent());
    assertEquals(DecisionOutcome.APPROVE, decisionOpt.get().getOutcome());
  }

  private TenantId seedTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(tenantId, "Org " + tenantId.value(), Instant.now()));
    return tenantId;
  }

  private RepositoryId seedRepository(TenantId tenantId, String name) {
    RepositoryId repoId = RepositoryId.generate();
    repositoryRepository.save(new Repository(
        repoId, tenantId, Repository.ProviderType.GITHUB, "ext-" + UUID.randomUUID(),
        name, "https://github.com/example/" + name, "main", Instant.now()));
    return repoId;
  }

  private void seedActivePolicy(TenantId tenantId) {
    Policy policy = new Policy(
        PolicyId.generate(),
        tenantId,
        "Default Active Policy",
        "Policy for beta dispatcher testing",
        PolicyStatus.ACTIVE,
        new PolicyVersion("1.0"),
        List.of(new PolicyRule("rule-1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Default approve rule")),
        Instant.now());
    policyRepository.save(policy);
  }
}
