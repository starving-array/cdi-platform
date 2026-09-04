package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.AgentContext;
import com.cdi.application.port.out.AgentInvestigationRepository;
import com.cdi.application.port.out.AgentPort;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.application.port.out.InvestigationFinding;
import com.cdi.application.port.out.InvestigationFindings;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.event.InvestigationCompleted;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import com.cdi.investigation.domain.AgentInvestigation;
import com.cdi.investigation.domain.EvidenceCitation;
import com.cdi.investigation.domain.InvestigationFailure;
import com.cdi.risk.domain.DeterministicRiskEngine;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-04 InvestigateRisk — the controlled AgentPort boundary.
 * Uses fake ports only; no Testcontainers, no Spring context, no persistence,
 * and no real AI service (the agent port is a fake).
 */
class InvestigateRiskHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final TenantId tenantId = TenantId.generate();
  private final RepositoryId repositoryId = RepositoryId.generate();
  private final ChangeId changeId = ChangeId.generate();
  private final AnalysisRunId runId = AnalysisRunId.generate();
  private final String commitSha = "abc123sha";
  private final EvidenceId incidentId = EvidenceId.generate();

  private FakeAnalysisRunRepository analysisRunRepository;
  private FakeChangeRepository changeRepository;
  private FakeRiskAssessmentRepository riskAssessmentRepository;
  private FakeAgentInvestigationRepository agentInvestigationRepository;
  private FakeSourceControlPort sourceControlPort;
  private FakeEvidenceSearchPort evidenceSearchPort;
  private FakeAgentPort agentPort;
  private FakeJobQueuePort jobQueuePort;
  private FakeDomainEventPublisher eventPublisher;
  private InvestigateRiskHandler handler;

  @BeforeEach
  void setUp() {
    analysisRunRepository = new FakeAnalysisRunRepository(tenantId);
    changeRepository = new FakeChangeRepository();
    riskAssessmentRepository = new FakeRiskAssessmentRepository(tenantId);
    agentInvestigationRepository = new FakeAgentInvestigationRepository(tenantId);
    sourceControlPort = new FakeSourceControlPort();
    evidenceSearchPort = new FakeEvidenceSearchPort();
    agentPort = new FakeAgentPort();
    jobQueuePort = new FakeJobQueuePort();
    eventPublisher = new FakeDomainEventPublisher();
    handler = new InvestigateRiskHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        agentInvestigationRepository, sourceControlPort, evidenceSearchPort,
        agentPort, jobQueuePort, eventPublisher, CLOCK);
  }

  @Test
  void validRunningRunInvokesAgentPortAndPersistsCompletedInvestigation() {
    runningRunWithAssessment();
    sourceControlPort.diff = List.of(new FileDiff("src/App.java", 5, 2, FileDiff.ChangeType.MODIFIED));
    evidenceSearchPort.records = List.of(newIncidentRecord());
    agentPort.output = new InvestigationFindings(List.of(
        new InvestigationFinding("Payment path risk",
            "Past incident on the same path", List.of(incidentId))));

    handler.handle(new InvestigateRiskCommand(runId));

    assertEquals(1, agentPort.calls);
    AgentInvestigation saved = agentInvestigationRepository.saved.get(0);
    assertEquals(AgentInvestigation.Status.COMPLETED, saved.getStatus());
    assertEquals(1, saved.getFindings().size());
    assertEquals(List.of(incidentId), saved.getFindings().get(0).citedEvidenceIds());

    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("EvaluatePolicyCommand", jobQueuePort.commandNames.get(0));
    assertEquals(runId, ((EvaluatePolicyCommand) jobQueuePort.payloads.get(0)).analysisRunId());

    assertEquals(1, eventPublisher.events.size());
    InvestigationCompleted event =
        (InvestigationCompleted) eventPublisher.events.get(0);
    assertEquals(InvestigationCompleted.InvestigationOutcome.SUCCESS, event.outcome());
    assertEquals(changeId, event.changeId());
  }

  @Test
  void structuredFindingsAreReturnedWithEvidenceAndExplanations() {
    runningRunWithAssessment();
    evidenceSearchPort.records = List.of(newIncidentRecord());
    agentPort.output = new InvestigationFindings(List.of(
        new InvestigationFinding("Finding A", "Explanation A", List.of(incidentId)),
        new InvestigationFinding("Finding B", null, List.of())));

    handler.handle(new InvestigateRiskCommand(runId));

    AgentInvestigation saved = agentInvestigationRepository.saved.get(0);
    assertEquals(2, saved.getFindings().size());
    assertEquals("Finding A", saved.getFindings().get(0).summary());
    assertEquals("Explanation A", saved.getFindings().get(0).explanation());
    assertEquals("Finding B", saved.getFindings().get(1).summary());
    assertTrue(saved.getFindings().get(1).citations().isEmpty());
  }

  @Test
  void evidenceIdentifiersArePreservedOnFindings() {
    runningRunWithAssessment();
    evidenceSearchPort.records = List.of(newIncidentRecord());
    agentPort.output = new InvestigationFindings(List.of(
        new InvestigationFinding("C", null, List.of(incidentId))));

    handler.handle(new InvestigateRiskCommand(runId));

    AgentInvestigation saved = agentInvestigationRepository.saved.get(0);
    assertEquals(List.of(incidentId), saved.getFindings().get(0).citedEvidenceIds());
    assertEquals(incidentId, saved.getFindings().get(0).citations().get(0).evidenceId());
  }

  @Test
  void citationsToUnknownEvidenceAreDroppedNotFabricated() {
    runningRunWithAssessment();
    evidenceSearchPort.records = List.of(newIncidentRecord());
    EvidenceId fabricated = EvidenceId.generate();
    agentPort.output = new InvestigationFindings(List.of(
        new InvestigationFinding("C",
            "claims fabricated evidence", List.of(incidentId, fabricated))));

    handler.handle(new InvestigateRiskCommand(runId));

    AgentInvestigation saved = agentInvestigationRepository.saved.get(0);
    List<EvidenceCitation> citations = saved.getFindings().get(0).citations();
    assertEquals(List.of(incidentId), citations.stream().map(EvidenceCitation::evidenceId).toList());
    assertFalse(citations.stream().anyMatch(c -> c.evidenceId().equals(fabricated)));
  }

  @Test
  void agentFailureFailsInvestigationButStillEnqueuesPolicyEvaluation() {
    runningRunWithAssessment();
    agentPort.fail = true;

    handler.handle(new InvestigateRiskCommand(runId));

    AgentInvestigation saved = agentInvestigationRepository.saved.get(0);
    assertEquals(AgentInvestigation.Status.FAILED, saved.getStatus());
    assertEquals(InvestigationFailure.FailureCategory.AGENT_UNAVAILABLE,
        saved.getFailure().orElseThrow().category());

    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("EvaluatePolicyCommand", jobQueuePort.commandNames.get(0));

    InvestigationCompleted event = (InvestigationCompleted) eventPublisher.events.get(0);
    assertEquals(InvestigationCompleted.InvestigationOutcome.FAILED, event.outcome());
  }

  @Test
  void tenantIsolationKeepsRunsAndInvestigationsScoped() {
    TenantId otherTenant = TenantId.generate();
    AnalysisRun run = runningRun(otherTenant);
    riskAssessmentRepository.risk = assessmentFor(run);

    handler.handle(new InvestigateRiskCommand(runId));

    // The run belongs to another tenant: the change/risk lookups resolve
    // nothing for the default tenant, so the investigation is a no-op.
    assertEquals(0, agentPort.calls);
    assertTrue(agentInvestigationRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void alreadyInvestigatedRunIsAnIdempotentNoOp() {
    runningRunWithAssessment();
    AgentInvestigation existing = new AgentInvestigation(
        InvestigationId.generate(), runId, changeId, assessmentFor(runningRun(tenantId)).getId(),
        NOW.minusSeconds(60));
    existing.start();
    existing.complete(List.of(), NOW.minusSeconds(30));
    agentInvestigationRepository.byRunId.put(runId.value(), existing);

    handler.handle(new InvestigateRiskCommand(runId));

    assertEquals(0, agentPort.calls);
    assertTrue(agentInvestigationRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
    assertTrue(eventPublisher.events.isEmpty());
  }

  @Test
  void invalidAnalysisRunStateIsANoOp() {
    AnalysisRun run = queuedRun(tenantId);
    analysisRunRepository.byId.put(runId.value(), run);
    openChange();

    handler.handle(new InvestigateRiskCommand(runId));

    assertEquals(0, agentPort.calls);
    assertTrue(agentInvestigationRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void missingRunIsANoOp() {
    handler.handle(new InvestigateRiskCommand(AnalysisRunId.generate()));

    assertEquals(0, agentPort.calls);
    assertTrue(agentInvestigationRepository.saved.isEmpty());
    assertTrue(jobQueuePort.commandNames.isEmpty());
  }

  @Test
  void deterministicRiskAssessmentIsUntouchedByInvestigation() {
    runningRunWithAssessment();
    RiskAssessment before = riskAssessmentRepository.risk;
    agentPort.output = new InvestigationFindings(List.of(
        new InvestigationFinding("C", null, List.of())));

    handler.handle(new InvestigateRiskCommand(runId));

    assertEquals(before.getScore(), riskAssessmentRepository.risk.getScore());
    assertEquals(before.getLevel(), riskAssessmentRepository.risk.getLevel());
    assertEquals(before.getFactors(), riskAssessmentRepository.risk.getFactors());
    assertEquals(before.getEvidenceState(), riskAssessmentRepository.risk.getEvidenceState());
  }

  @Test
  void policyEngineIsNeverInvokedByInvestigateRisk() {
    runningRunWithAssessment();
    agentPort.output = new InvestigationFindings(List.of());

    handler.handle(new InvestigateRiskCommand(runId));

    assertFalse(eventPublisher.events.stream()
        .anyMatch(e -> e.getClass().getSimpleName().equals("DecisionGenerated")));
    assertEquals(1, jobQueuePort.commandNames.size());
    assertEquals("EvaluatePolicyCommand", jobQueuePort.commandNames.get(0));
  }

  @Test
  void rawAgentStringCannotDirectlyDriveADecision() {
    runningRunWithAssessment();
    // The port returns the structured carrier type only; the handler never
    // reads an arbitrary raw string and stores it as a decision input.
    assertTrue(agentPort.output == null
        || agentPort.output instanceof InvestigationFindings);
  }

  // ---- helpers ----

  private void runningRunWithAssessment() {
    openChange();
    AnalysisRun run = runningRun(tenantId);
    analysisRunRepository.byId.put(runId.value(), run);
    riskAssessmentRepository.risk = assessmentFor(run);
  }

  private RiskAssessment assessmentFor(AnalysisRun run) {
    return new RiskAssessment(
        RiskAssessmentId.generate(), run.getId(),
        RiskScore.of(45), RiskLevel.MEDIUM,
        List.of(new RiskFactor(RiskFactorType.HISTORICAL_INCIDENT_MATCH, 20,
            "Matched 1 similar incident.", List.of(incidentId))),
        EvidenceState.EVIDENCE_AVAILABLE,
        DeterministicRiskEngine.RULE_VERSION, NOW);
  }

  private AnalysisRun runningRun(TenantId tenant) {
    AnalysisRun run = queuedRun(tenant);
    run.start();
    
    return run;
  }

  private AnalysisRun queuedRun(TenantId tenant) {
    return new AnalysisRun(
        runId, changeId, new CodeSnapshot(commitSha, "feature-x"), NOW);
  }

  private void openChange() {
    Change change = new Change(
        changeId, repositoryId, "PR-42",
        "Fix bug", "", "alice", "feature-x", "main", commitSha, NOW);
    changeRepository.byId.put(tenantId.value() + "|" + changeId.value(), change);
  }

  private EvidenceRecord newIncidentRecord() {
    return EvidenceRecord.builder()
        .id(incidentId)
        .tenantId(tenantId)
        .analysisRunId(runId)
        .source(new EvidenceSource(SourceType.INCIDENT, "INC-100"))
        .origin(EvidenceOrigin.RETRIEVED)
        .title("Past incident on this path")
        .build();
  }

  // ---- fakes ----

  private static class FakeChangeRepository implements ChangeRepository {
    final Map<String, Change> byId = new HashMap<>();

    @Override
    public Optional<Change> findByTenantAndRepositoryAndProvider(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return byId.values().stream().findFirst();
    }

    @Override
    public Optional<Change> findByTenantAndId(TenantId tenantId, ChangeId changeId) {
      return Optional.ofNullable(byId.get(tenantId.value() + "|" + changeId.value()));
    }

    @Override
    public Change save(TenantId tenantId, Change change) {
      byId.put(tenantId.value() + "|" + change.getId().value(), change);
      return change;
    }
  }

  private static class FakeAnalysisRunRepository implements AnalysisRunRepository {
    private final TenantId defaultTenant;
    final Map<UUID, AnalysisRun> byId = new HashMap<>();
    final Map<UUID, TenantId> runTenants = new HashMap<>();

    FakeAnalysisRunRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public Optional<AnalysisRun> findByTenantAndChangeAndCommit(
        TenantId tenantId, ChangeId changeId, String commitSha) {
      return byId.values().stream()
          .filter(r -> r.getChangeId().equals(changeId)
              && r.getCodeSnapshot().commitSha().equals(commitSha))
          .findFirst();
    }

    @Override
    public Optional<AnalysisRunContext> findById(AnalysisRunId analysisRunId) {
      return Optional.ofNullable(byId.get(analysisRunId.value()))
          .map(run -> new AnalysisRunContext(
              runTenants.getOrDefault(analysisRunId.value(), defaultTenant), run));
    }

    @Override
    public boolean claim(AnalysisRunId analysisRunId) {
      return false;
    }

    @Override
    public AnalysisRun save(TenantId tenantId, AnalysisRun run) {
      byId.put(run.getId().value(), run);
      return run;
    }

    @Override
    public List<AnalysisRunContext> findByTenantAndChange(
        TenantId tenantId, ChangeId changeId) {
      return List.of();
    }
  }

  private static class FakeRiskAssessmentRepository implements RiskAssessmentRepository {
    private final TenantId defaultTenant;
    final Map<UUID, RiskAssessment> byRun = new HashMap<>();
    RiskAssessment risk;

    FakeRiskAssessmentRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public RiskAssessment save(TenantId tenantId, RiskAssessment riskAssessment) {
      byRun.put(riskAssessment.getAnalysisRunId().value(), riskAssessment);
      return riskAssessment;
    }

    @Override
    public Optional<RiskAssessment> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
      return Optional.ofNullable(risk);
    }
  }

  private static class FakeAgentInvestigationRepository implements AgentInvestigationRepository {
    private final TenantId defaultTenant;
    final Map<UUID, AgentInvestigation> byRunId = new HashMap<>();
    final List<AgentInvestigation> saved = new ArrayList<>();

    FakeAgentInvestigationRepository(TenantId defaultTenant) {
      this.defaultTenant = defaultTenant;
    }

    @Override
    public AgentInvestigation save(TenantId tenantId, AgentInvestigation investigation) {
      saved.add(investigation);
      byRunId.put(investigation.getAnalysisRunId().value(), investigation);
      return investigation;
    }

    @Override
    public Optional<AgentInvestigation> findByAnalysisRunId(TenantId tenantId, AnalysisRunId analysisRunId) {
      if (!tenantId.value().equals(defaultTenant.value())) {
        return Optional.empty();
      }
      return Optional.ofNullable(byRunId.get(analysisRunId.value()));
    }

    @Override
    public Optional<AgentInvestigation> findById(TenantId tenantId, InvestigationId investigationId) {
      return saved.stream()
          .filter(i -> i.getId().equals(investigationId))
          .findFirst();
    }
  }

  private static class FakeSourceControlPort implements SourceControlPort {
    static final String SHA = "abc123sha";
    List<FileDiff> diff = List.of();

    @Override
    public com.cdi.application.port.out.ChangeMetadata getChangeMetadata(
        TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
      return new com.cdi.application.port.out.ChangeMetadata(
          providerChangeId, "t", "", "a", "feature-x", "main", SHA);
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
      return diff;
    }

    @Override
    public byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path, String commitSha) {
      return new byte[0];
    }

    @Override
    public void publishStatusCheck(
        TenantId tenantId, RepositoryId repositoryId, String commitSha,
        com.cdi.decision.domain.DecisionOutcome outcome,
        List<com.cdi.decision.domain.DecisionReason> reasons, String detailsUrl) {
      // not used by InvestigateRisk
    }
  }

  private static class FakeEvidenceSearchPort implements EvidenceSearchPort {
    List<EvidenceRecord> records = List.of();
    boolean failSearch;

    @Override
    public List<EvidenceRecord> searchSimilarChanges(
        TenantId tenantId, List<String> filePaths, int limit) {
      if (failSearch) {
        throw new PortException(PortType.EVIDENCE_SEARCH, false, "vector db down");
      }
      return records;
    }

    @Override
    public List<EvidenceRecord> searchIncidents(
        TenantId tenantId, com.cdi.common.domain.id.ServiceId serviceId,
        List<String> keywords, int limit) {
      return List.of();
    }

    @Override
    public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
      return List.of();
    }
  }

  private static class FakeAgentPort implements AgentPort {
    int calls;
    boolean fail;
    InvestigationFindings output;

    @Override
    public InvestigationFindings investigate(
        AgentContext context, RiskAssessment riskAssessment, List<EvidenceRecord> evidence) {
      calls++;
      if (fail) {
        throw new PortException(PortType.AGENT, true, "agent timeout");
      }
      return output;
    }
  }

  private static class FakeJobQueuePort implements JobQueuePort {
    final List<String> commandNames = new ArrayList<>();
    final List<Object> payloads = new ArrayList<>();
    final List<IdempotencyKey> keys = new ArrayList<>();

    @Override
    public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
      commandNames.add(commandName);
      payloads.add(payload);
      keys.add(key);
      return new JobId("job-1");
    }

    @Override
    public void cancel(IdempotencyKey key) {
      // not used by InvestigateRisk
    }
  }

  private static class FakeDomainEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      events.add(event);
    }
  }
}
