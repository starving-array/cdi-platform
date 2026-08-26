package com.cdi.application.attribution;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetDeploymentAttributionQuery;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionAttributionRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.DeploymentRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import com.cdi.attribution.domain.AttributionClassification;
import com.cdi.attribution.domain.DecisionAttribution;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.AttributionId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.deployment.domain.Deployment;
import com.cdi.deployment.domain.DeploymentOutcome;
import com.cdi.deployment.domain.OutcomeType;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GetDeploymentAttributionQueryService {

  private final DecisionAttributionRepository decisionAttributionRepository;
  private final DeploymentRepository deploymentRepository;
  private final AnalysisRunRepository analysisRunRepository;
  private final RiskAssessmentRepository riskAssessmentRepository;
  private final DecisionRecordRepository decisionRecordRepository;
  private final Clock clock;

  public GetDeploymentAttributionQueryService(
      DecisionAttributionRepository decisionAttributionRepository,
      DeploymentRepository deploymentRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      DecisionRecordRepository decisionRecordRepository,
      Clock clock) {
    this.decisionAttributionRepository = Objects.requireNonNull(decisionAttributionRepository, "decisionAttributionRepository");
    this.deploymentRepository = Objects.requireNonNull(deploymentRepository, "deploymentRepository");
    this.analysisRunRepository = Objects.requireNonNull(analysisRunRepository, "analysisRunRepository");
    this.riskAssessmentRepository = Objects.requireNonNull(riskAssessmentRepository, "riskAssessmentRepository");
    this.decisionRecordRepository = Objects.requireNonNull(decisionRecordRepository, "decisionRecordRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public GetDeploymentAttributionQueryService(
      DecisionAttributionRepository decisionAttributionRepository,
      DeploymentRepository deploymentRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      DecisionRecordRepository decisionRecordRepository) {
    this(decisionAttributionRepository, deploymentRepository, analysisRunRepository,
        riskAssessmentRepository, decisionRecordRepository, Clock.systemUTC());
  }

  public DecisionAttribution handle(GetDeploymentAttributionQuery query) {
    // 1. Authorization: ENGINEER and TENANT_ADMIN allowed
    Actor.Role role = query.actor().role();
    if (role != Actor.Role.ENGINEER && role != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED, "Only ENGINEER or TENANT_ADMIN can view attribution");
    }

    // 2. Check if already attributed and persisted (immutable correlation)
    Optional<DecisionAttribution> existing = decisionAttributionRepository.findByTenantIdAndDeploymentId(
        query.tenantId(), query.deploymentId());
    if (existing.isPresent()) {
      return existing.get();
    }

    // 3. Load deployment for tenant
    Deployment deployment = deploymentRepository.findByTenantIdAndId(query.tenantId(), query.deploymentId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.DEPLOYMENT_NOT_FOUND, "Deployment not found"));

    // If deployment has no outcome yet, attribution cannot be computed
    if (deployment.getOutcome().isEmpty()) {
      throw new ApplicationException(ApplicationError.ATTRIBUTION_NOT_FOUND, "Deployment has no outcome to attribute");
    }

    DeploymentOutcome deploymentOutcome = deployment.getOutcome().get();
    OutcomeType outcomeType = deploymentOutcome.getOutcome();

    // 4. Correlate with AnalysisRun for (tenantId, commitSha)
    List<AnalysisRunContext> runs = analysisRunRepository.findByTenantAndCommit(
        query.tenantId(), deployment.getCommitSha());

    AnalysisRunId runId = null;
    RiskAssessmentId riskId = null;
    DecisionId decisionId = null;
    RiskLevel predictedRiskLevel = null;
    DecisionOutcome decisionOutcome = null;
    boolean hasHumanOverride = false;
    AttributionClassification classification = AttributionClassification.UNATTRIBUTED;

    if (!runs.isEmpty()) {
      // Pick the most recent completed run for the commit
      Optional<AnalysisRunContext> completedRun = runs.stream()
          .filter(r -> r.run().getStatus() == AnalysisRun.Status.COMPLETED)
          .findFirst();

      if (completedRun.isPresent()) {
        AnalysisRun run = completedRun.get().run();
        runId = run.getId();

        Optional<RiskAssessment> riskOpt = riskAssessmentRepository.findByAnalysisRunId(query.tenantId(), runId);
        Optional<DecisionRecord> decisionOpt = decisionRecordRepository.findByAnalysisRunId(query.tenantId(), runId);

        if (riskOpt.isPresent() && decisionOpt.isPresent()) {
          RiskAssessment risk = riskOpt.get();
          DecisionRecord decision = decisionOpt.get();

          riskId = risk.getId();
          decisionId = decision.getId();
          predictedRiskLevel = risk.getLevel();
          decisionOutcome = decision.getOutcome();
          hasHumanOverride = decision.getOverride().isPresent();

          classification = DecisionAttribution.classify(outcomeType, predictedRiskLevel);
        }
      }
    }

    Instant now = clock.instant();
    DecisionAttribution attribution = new DecisionAttribution(
        AttributionId.generate(),
        query.tenantId(),
        deployment.getId(),
        deployment.getServiceId(),
        runId,
        riskId,
        decisionId,
        classification,
        outcomeType,
        predictedRiskLevel,
        decisionOutcome,
        hasHumanOverride,
        now,
        now);

    return decisionAttributionRepository.save(attribution);
  }
}