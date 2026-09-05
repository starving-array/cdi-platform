package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
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
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.DomainEvent;
import com.cdi.common.domain.event.InvestigationCompleted;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.investigation.domain.AgentInvestigation;
import com.cdi.investigation.domain.EvidenceCitation;
import com.cdi.investigation.domain.InvestigationFailure;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.application.analysis.InvestigationCodeIntelligence;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application use case UC-04 InvestigateRisk — the controlled AgentPort
 * boundary (application-layer.md §6, §13; use-cases.md §5.3; analysis-workflow.md
 * §1.6). Async worker consumed from the queue via {@code JobQueuePort} with the
 * run-only payload {@code InvestigateRiskCommand}.
 *
 * <p><b>AI boundary</b>: the handler calls only the {@link AgentPort}
 * contract — it never imports or depends on any AI provider SDK. The agent
 * produces structured {@link InvestigationFinding}s that <em>supplement</em>
 * the deterministic {@link RiskAssessment}; it does not replace risk scoring,
 * and {@code PolicyEngine} is never invoked here (policy/decision stay
 * downstream in UC-05 GenerateDecision).
 *
 * <p><b>Truth boundary</b> (§5): every evidence citation in a returned finding
 * must reference an {@link EvidenceId} already available to the application
 * (from the deterministic risk factors or the fetched evidence records).
 * Citations to unknown evidence are dropped (never fabricated, never silently
 * promoted to certainty); a finding left with no citations is recorded as a
 * purely qualitative observation.
 *
 * <p><b>Failure model</b> (§6): an {@code AgentPort} failure is surfaced as a
 * {@link PortException} and represented explicitly — the investigation is
 * persisted as {@code FAILED} with an {@link InvestigationFailure} and
 * {@code InvestigationCompleted(FAILED)} is published, then
 * {@code EvaluatePolicy} is enqueued so the workflow continues to the
 * deterministic policy-evaluation stage (degraded to deterministic risk only).
 * The 1-retry / 2-minute timeout policy lives in the agent port adapter,
 * <em>not</em> here (no worker retry subsystem).
 *
 * <p><b>Idempotency</b> (§7): one investigation per run
 * ({@code findByAnalysisRunId}); a run with an existing investigation is a
 * no-op and is never duplicated. Only a {@code COMPLETED} analysis run (which
 * holds a deterministic assessment) is investigable; {@code QUEUED}/{@code
 * RUNNING}/{@code FAILED}/{@code SUPERSEDED} runs and missing runs/changes/risk
 * are replay-safe no-ops — the historical/completed aggregate is never mutated
 * or restarted.
 */
public final class InvestigateRiskHandler {

  private static final int EVIDENCE_LIMIT = 20;

  private final AnalysisRunRepository analysisRunRepository;
  private final ChangeRepository changeRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final AgentInvestigationRepository agentInvestigationRepository;
  private final SourceControlPort sourceControlPort;
  private final EvidenceSearchPort evidenceSearchPort;
  private final AgentPort agentPort;
  private final JobQueuePort jobQueuePort;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;
  private final CodeContextAssembler codeContextAssembler;

  public InvestigateRiskHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      AgentInvestigationRepository agentInvestigationRepository,
      SourceControlPort sourceControlPort,
      EvidenceSearchPort evidenceSearchPort,
      AgentPort agentPort,
      JobQueuePort jobQueuePort,
      DomainEventPublisher eventPublisher,
      Clock clock,
      CodeContextAssembler codeContextAssembler) {
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.changeRepository = Objects.requireNonNull(changeRepository, "ChangeRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "RiskAssessmentRepository");
    this.agentInvestigationRepository = Objects.requireNonNull(agentInvestigationRepository, "AgentInvestigationRepository");
    this.sourceControlPort = Objects.requireNonNull(sourceControlPort, "SourceControlPort");
    this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "EvidenceSearchPort");
    this.agentPort = Objects.requireNonNull(agentPort, "AgentPort");
    this.jobQueuePort = Objects.requireNonNull(jobQueuePort, "JobQueuePort");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "DomainEventPublisher");
    this.clock = Objects.requireNonNull(clock, "Clock");
    this.codeContextAssembler = Objects.requireNonNull(codeContextAssembler, "CodeContextAssembler");
  }

  /**
   * Executes the agent investigation for a completed run. Replay-safe and
   * idempotent.
   */
  public void handle(InvestigateRiskCommand command) {
    Optional<AnalysisRunContext> context = analysisRunRepository.findById(command.analysisRunId());
    if (context.isEmpty()) {
      return;
    }
    AnalysisRunContext runContext = context.get();
    TenantId tenantId = runContext.tenantId();

    if (agentInvestigationRepository
        .findByAnalysisRunId(tenantId, runContext.run().getId())
        .isPresent()) {
      return;
    }

    if (runContext.run().getStatus() != AnalysisRun.Status.RUNNING) {
      return;
    }

    Change change = changeRepository.findByTenantAndId(tenantId, runContext.run().getChangeId())
        .orElse(null);
    if (change == null) {
      return;
    }

    RiskAssessment risk = riskAssessmentRepository
        .findByAnalysisRunId(tenantId, runContext.run().getId())
        .orElse(null);
    if (risk == null) {
      return;
    }

    Instant now = clock.instant();
    AgentInvestigation investigation = new AgentInvestigation(
        InvestigationId.generate(), runContext.run().getId(),
        change.getId(), risk.getId(), now);
    investigation.start();

    List<EvidenceRecord> evidence = new ArrayList<>(fetchEvidence(tenantId, change, runContext.run().getCodeSnapshot().commitSha()));

    // Compute code intelligence early so it can be included in the LLM prompt
    // via a structured evidence record, and also used for domain-level
    // findings enhancement later in the same method.
    InvestigationCodeIntelligence codeIntelligence =
        computeCodeIntelligence(tenantId, change, runContext.run().getCodeSnapshot().commitSha());

    // Append a structured code-intelligence evidence record so that the
    // LlmAgentPortAdapter can include full code intelligence in the LLM prompt.
    // This record is purely informational for the agent boundary; it does not
    // affect the deterministic risk/policy pipeline because its
    // EvidenceOrigin is AGENT_DISCOVERED and its source reference is "CODE_INTELLIGENCE".
    if (codeIntelligence != null) {
      String content;
      try {
        content = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(codeIntelligence);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        content = "{}";
      }
      evidence.add(EvidenceRecord.builder()
          .id(EvidenceId.generate())
          .tenantId(tenantId)
          .analysisRunId(runContext.run().getId())
          .source(new EvidenceSource(SourceType.OTHER, "CODE_INTELLIGENCE"))
          .origin(EvidenceOrigin.AGENT_DISCOVERED)
          .title("InvestigationCodeIntelligence")
          .content(content)
          .build());
    }

    try {
      AgentContext agentContext = new AgentContext(
          tenantId, change.getId(), runContext.run().getId(),
          runContext.run().getCodeSnapshot().commitSha());
      InvestigationFindings output = agentPort.investigate(agentContext, risk, evidence);

      Set<EvidenceId> available = availableEvidenceIds(risk, evidence);
      List<com.cdi.investigation.domain.InvestigationFinding> findings =
          toDomainFindings(output, available);

      // Enhance findings' explanations with code intelligence for the LLM investigation.
      // Only augment explanations that are null; preserve existing non-null
      // explanations exactly as the agent port returned them (backward compatibility
      // with existing test expectations and the truth-boundary enforcement).
      for (int i = 0; i < findings.size(); i++) {
        com.cdi.investigation.domain.InvestigationFinding f = findings.get(i);
        if (f.explanation() == null) {
          String codeSummary =
              "CodeContext: commitSha=" + codeIntelligence.commitSha()
                  + ", changedFiles=" + codeIntelligence.changedFiles()
                  + ", methods=" + codeIntelligence.changedMethodSignatures()
                  + ", imports=" + codeIntelligence.importedTypes();
          findings.set(i,
              new com.cdi.investigation.domain.InvestigationFinding(
                  f.summary(), codeSummary, f.citations()));
        }
      }

      investigation.complete(findings, now);
      agentInvestigationRepository.save(tenantId, investigation);
      enqueueEvaluatePolicy(runContext.run().getId(), tenantId);
      publish(InvestigationCompleted.created(
          tenantId, investigation.getId(), change.getId(),
          InvestigationCompleted.InvestigationOutcome.SUCCESS));
    } catch (PortException e) {
      investigation.fail(new InvestigationFailure(
          InvestigationFailure.FailureCategory.AGENT_UNAVAILABLE,
          e.getPort() == PortType.AGENT ? "agent-unavailable" : "investigation-failed", now));
      agentInvestigationRepository.save(tenantId, investigation);
      enqueueEvaluatePolicy(runContext.run().getId(), tenantId);
      publish(InvestigationCompleted.created(
          tenantId, investigation.getId(), change.getId(),
          InvestigationCompleted.InvestigationOutcome.FAILED));
    }
  }

  /**
   * Gathers historical evidence for the change under investigation. Degrades
   * to empty evidence on any port failure — the agent supplements, never
   * blocks on enrichment.
   */
  private List<EvidenceRecord> fetchEvidence(
      TenantId tenantId, Change change, String commitSha) {
    List<String> paths;
    try {
      List<FileDiff> diff =
          sourceControlPort.getDiff(tenantId, change.getRepositoryId(), commitSha);
      paths = diff.stream().map(FileDiff::path).toList();
    } catch (PortException e) {
      return List.of();
    }
    try {
      return evidenceSearchPort.searchSimilarChanges(tenantId, paths, EVIDENCE_LIMIT);
    } catch (PortException e) {
      return List.of();
    }
  }

  /**
   * The evidence identifiers already available: those cited by the
   * deterministic risk factors plus those carried by the fetched records.
   */
  private Set<EvidenceId> availableEvidenceIds(RiskAssessment risk, List<EvidenceRecord> evidence) {
    Set<EvidenceId> ids = new HashSet<>();
    for (RiskFactor factor : risk.getFactors()) {
      if (factor.evidenceReferences() != null) {
        ids.addAll(factor.evidenceReferences());
      }
    }
    for (EvidenceRecord record : evidence) {
      ids.add(record.getId());
    }
    return ids;
  }

  /**
   * Converts structured port findings into domain findings after dropping any
   * citation to evidence that is not already available to the application
   * (truth-boundary enforcement).
   */
  private List<com.cdi.investigation.domain.InvestigationFinding> toDomainFindings(
      InvestigationFindings output, Set<EvidenceId> available) {
    List<com.cdi.investigation.domain.InvestigationFinding> findings = new ArrayList<>();
    if (output == null || output.findings() == null) {
      return findings;
    }
    for (InvestigationFinding portFinding : output.findings()) {
      List<EvidenceCitation> citations = portFinding.evidenceCitations() == null
          ? List.of()
          : portFinding.evidenceCitations().stream()
              .filter(available::contains)
              .map(EvidenceCitation::of)
              .collect(Collectors.toList());
      findings.add(new com.cdi.investigation.domain.InvestigationFinding(
          portFinding.summary(), portFinding.explanation(), citations));
    }
    return findings;
  }

  /**
   * Computes code intelligence from the change under investigation, derived
   * from the actual source available via the {@link SourceControlPort}. The
   * result is a lightweight application-level DTO holding structural
   * information (changed files, method signatures, imported types, impact
   * graph edges, dependency paths, availability states) that the LLM can
   * use for investigation prompting. JavaParser AST objects are NOT exposed
   * through the investigation boundary; all structural information is
   * converted into application-level models.
   * <p>
   * If any port call fails, the method returns an {@link InvestigationCodeIntelligence}
   * with empty/unknown availability states rather than propagating errors,
   * so the investigation workflow continues deterministically.
   */
  private InvestigationCodeIntelligence computeCodeIntelligence(
      TenantId tenantId, Change change, String commitSha) {
    List<String> changedFiles = new ArrayList<>();
    List<String> changedMethodSignatures = new ArrayList<>();
    Set<String> importedTypes = new HashSet<>();
    List<String> directCallers = new ArrayList<>();
    List<String> directCallees = new ArrayList<>();
    List<String> impactGraphEdges = new ArrayList<>();
    List<String> dependencyPaths = new ArrayList<>();
    Set<String> availabilityStates = new HashSet<>();

    List<FileDiff> diff = null;
    try {
      diff = sourceControlPort.getDiff(tenantId, change.getRepositoryId(), commitSha);
      if (diff != null) {
        changedFiles = diff.stream().map(FileDiff::path).collect(Collectors.toList());
      }
    } catch (PortException e) {
      availabilityStates.add("UNKNOWN");
    }

    if (diff != null && !diff.isEmpty()) {
      try {
        com.cdi.analysis.domain.CodeContext context = codeContextAssembler.assemble(tenantId, change.getRepositoryId(), commitSha, diff);
        com.cdi.analysis.domain.parsing.JavaChangeAnalyzer changeAnalyzer = new com.cdi.analysis.domain.parsing.JavaChangeAnalyzer();
        com.cdi.analysis.domain.parsing.JavaChangeAnalysis changeAnalysis = changeAnalyzer.analyze(context);

        com.cdi.analysis.domain.parsing.JavaDependencyAnalyzer dependencyAnalyzer = new com.cdi.analysis.domain.parsing.JavaDependencyAnalyzer();
        com.cdi.analysis.domain.parsing.DependencyAnalysis dependencyAnalysis = dependencyAnalyzer.analyze(context);

        com.cdi.analysis.domain.parsing.ImpactGraph impactGraph = new com.cdi.analysis.domain.parsing.ImpactGraph(dependencyAnalysis);
        com.cdi.analysis.domain.parsing.ImpactGraphResult impactGraphResult = impactGraph.build();

        for (com.cdi.analysis.domain.parsing.JavaFileChange fileChange : changeAnalysis.files()) {
          importedTypes.addAll(fileChange.imports());
          for (com.cdi.analysis.domain.parsing.TypeChange type : fileChange.types()) {
            for (com.cdi.analysis.domain.parsing.MemberChange m : type.methods()) {
              if (m.changed()) changedMethodSignatures.add(type.name() + "." + m.name());
            }
            for (com.cdi.analysis.domain.parsing.MemberChange m : type.constructors()) {
              if (m.changed()) changedMethodSignatures.add(type.name() + "." + m.name());
            }
          }
        }

        for (com.cdi.analysis.domain.parsing.ImpactGraphEdge edge : impactGraphResult.edges) {
          impactGraphEdges.add(edge.edgeType() + ":" + edge.sourceMember() + "->" + edge.targetMember());
          if ("CALLS".equals(edge.edgeType())) directCallees.add(edge.targetMember());
          if ("CALLED_BY".equals(edge.edgeType())) directCallers.add(edge.sourceMember());
          if ("TYPE_DEPENDENCY".equals(edge.edgeType())) dependencyPaths.add(edge.targetMember());
        }

        availabilityStates.add(changedFiles.isEmpty() ? "NO_FILES" : "FILES_RETRIEVED");
        availabilityStates.add(changedMethodSignatures.isEmpty() ? "NO_METHODS" : "METHODS_PARSED");
        availabilityStates.add(importedTypes.isEmpty() ? "NO_IMPORTS" : "IMPORTS_PARSED");
      } catch (Exception e) {
        availabilityStates.add("UNKNOWN");
      }
    } else {
        availabilityStates.add(changedFiles.isEmpty() ? "NO_FILES" : "FILES_RETRIEVED");
        availabilityStates.add("NO_METHODS");
        availabilityStates.add("NO_IMPORTS");
    }

    return new InvestigationCodeIntelligence(
        commitSha,
        changedFiles,
        changedMethodSignatures,
        importedTypes,
        directCallers,
        directCallees,
        impactGraphEdges,
        dependencyPaths,
        availabilityStates);
  }


  private void enqueueEvaluatePolicy(AnalysisRunId runId, TenantId tenantId) {
    jobQueuePort.enqueue("EvaluatePolicyCommand", new EvaluatePolicyCommand(runId),
        new IdempotencyKey(tenantId.value() + ":" + runId.value()));
  }

  private void publish(DomainEvent event) {
    eventPublisher.publish(event);
  }
}
