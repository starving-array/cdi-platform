package com.cdi.application.port.out;

import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.ServiceDependency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the outbound port contracts are implementable by provider-neutral
 * fake adapters using only domain/application types — no Spring, HTTP, JPA,
 * SDK, or AI/library types leak through the interfaces.
 */
class PortContractTest {

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final AnalysisRunId analysisRunId = AnalysisRunId.generate();

  private final SourceControlPort sourceControlPort = new FakeSourceControlPort();
  private final SystemContextPort systemContextPort = new FakeSystemContextPort();
  private final EvidenceSearchPort evidenceSearchPort = new FakeEvidenceSearchPort();
  private final AgentPort agentPort = new FakeAgentPort();
  private final JobQueuePort jobQueuePort = new FakeJobQueuePort();

  @Test
  void sourceControlPortShouldReturnDomainDiffTypes() {
    List<FileDiff> diff = sourceControlPort.getDiff(tenantId, repositoryId, "abc123");

    assertEquals(1, diff.size());
    assertEquals("src/App.java", diff.get(0).path());
    assertNotNull(sourceControlPort.getChangeMetadata(tenantId, repositoryId, "PR-42"));
  }

  @Test
  void systemContextPortShouldReturnDomainCriticalityAndDependencies() {
    CriticalityTier tier = systemContextPort.getServiceCriticality(
        tenantId, repositoryId, List.of("src/App.java"));
    List<ServiceDependency> dependencies =
        systemContextPort.getDependencies(tenantId, ServiceId.generate());

    assertEquals(CriticalityTier.TIER_0, tier);
    assertEquals(1, dependencies.size());
  }

  @Test
  void evidenceSearchPortShouldReturnDomainEvidenceRecords() {
    List<EvidenceRecord> records =
        evidenceSearchPort.searchSimilarChanges(tenantId, List.of("src/App.java"), 5);

    assertEquals(1, records.size());
    assertEquals("Past incident", records.get(0).getTitle());
  }

  @Test
  void evidenceSearchPortShouldReturnSearchByQueryRecords() {
    List<EvidenceRecord> records =
        evidenceSearchPort.searchByQuery(tenantId, "kafka", 5);

    assertEquals(1, records.size());
    assertEquals("Query result", records.get(0).getTitle());
  }

  @Test
  void agentPortShouldReturnStructuredFindings() {
    RiskAssessment risk = new RiskAssessment(
        com.cdi.common.domain.id.RiskAssessmentId.generate(),
        analysisRunId,
        RiskScore.of(75),
        RiskLevel.HIGH,
        List.of(),
        EvidenceState.EVIDENCE_AVAILABLE,
        "v1",
        Instant.now());

    AgentContext context = new AgentContext(tenantId, ChangeId.generate(), analysisRunId, "abc123");
    InvestigationFindings findings =
        agentPort.investigate(context, risk, List.of());

    assertEquals(1, findings.findings().size());
    assertEquals("High risk verified", findings.findings().get(0).summary());
    assertEquals(1, findings.findings().get(0).evidenceCitations().size());
  }

  @Test
  void jobQueuePortShouldEnqueueAndCancel() {
    JobId jobId = jobQueuePort.enqueue(
        "AnalyzeChangeCommand", new Object(), new IdempotencyKey("key-1"));

    assertNotNull(jobId.value());

    jobQueuePort.cancel(new IdempotencyKey("key-1"));
    assertEquals(1, ((FakeJobQueuePort) jobQueuePort).cancelled.get());
  }

  @Test
  void publishStatusCheckShouldTranslateDecisionTypes() {
    DecisionReason reason = new DecisionReason("Large change", "rule-1", List.of());
    sourceControlPort.publishStatusCheck(
        tenantId, repositoryId, "abc123", DecisionOutcome.REVIEW_REQUIRED,
        List.of(reason), "https://app.cdi/run/1");

    assertEquals(1, ((FakeSourceControlPort) sourceControlPort).statusChecks.get());
  }

  private static class FakeSourceControlPort implements SourceControlPort {
    final AtomicInteger statusChecks = new AtomicInteger();

    @Override
    public ChangeMetadata getChangeMetadata(TenantId tenantId, RepositoryId repositoryId,
                                            String providerChangeId) {
      return new ChangeMetadata("PR-42", "title", "desc", "author", "main",
          "main", "abc123");
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      return List.of(new FileDiff("src/App.java", 10, 2, FileDiff.ChangeType.MODIFIED));
    }

    @Override
    public void publishStatusCheck(TenantId tenantId, RepositoryId repositoryId, String commitSha,
                                   DecisionOutcome outcome, List<DecisionReason> reasons,
                                   String detailsUrl) {
      statusChecks.incrementAndGet();
    }
  }

  private static class FakeSystemContextPort implements SystemContextPort {
    @Override
    public CriticalityTier getServiceCriticality(TenantId tenantId, RepositoryId repositoryId,
                                                 List<String> filePaths) {
      return CriticalityTier.TIER_0;
    }

    @Override
    public List<ServiceDependency> getDependencies(TenantId tenantId, ServiceId serviceId) {
      return List.of(new ServiceDependency(ServiceId.generate()));
    }
  }

  private static class FakeEvidenceSearchPort implements EvidenceSearchPort {
    @Override
    public List<EvidenceRecord> searchSimilarChanges(TenantId tenantId, List<String> filePaths,
                                                     int limit) {
      return List.of(record(tenantId, "Past incident"));
    }

    @Override
    public List<EvidenceRecord> searchIncidents(TenantId tenantId, ServiceId serviceId,
                                                List<String> keywords, int limit) {
      return List.of(record(tenantId, "Past incident 2"));
    }

    @Override
    public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
      return List.of(record(tenantId, "Query result"));
    }

    private EvidenceRecord record(TenantId tenantId, String title) {
      return EvidenceRecord.builder()
          .tenantId(tenantId)
          .analysisRunId(AnalysisRunId.generate())
          .source(new EvidenceSource(SourceType.INCIDENT, "INC-1"))
          .origin(EvidenceOrigin.RETRIEVED)
          .title(title)
          .build();
    }
  }

  private static class FakeAgentPort implements AgentPort {
    @Override
    public InvestigationFindings investigate(AgentContext context, RiskAssessment riskAssessment,
                                             List<EvidenceRecord> evidence) {
      EvidenceId cited = EvidenceId.generate();
      return new InvestigationFindings(List.of(
          new InvestigationFinding("High risk verified", "Large blast radius",
              List.of(cited))));
    }
  }

  private static class FakeJobQueuePort implements JobQueuePort {
    final AtomicInteger cancelled = new AtomicInteger();

    @Override
    public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
      return new JobId("job-1");
    }

    @Override
    public void cancel(IdempotencyKey key) {
      cancelled.incrementAndGet();
    }
  }
}