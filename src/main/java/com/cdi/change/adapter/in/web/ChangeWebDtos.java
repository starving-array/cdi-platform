package com.cdi.change.adapter.in.web;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.change.ChangeAnalysisView;
import com.cdi.change.domain.Change;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.risk.domain.RiskAssessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChangeWebDtos {

  private ChangeWebDtos() {}

  public record ChangeSummaryResponse(
      UUID id,
      UUID repositoryId,
      String providerChangeId,
      String title,
      String description,
      String author,
      String sourceBranch,
      String targetBranch,
      String latestCommitSha,
      String status,
      Instant createdAt,
      Instant updatedAt
  ) {
    public static ChangeSummaryResponse fromDomain(Change change) {
      return new ChangeSummaryResponse(
          change.getId().value(),
          change.getRepositoryId().value(),
          change.getProviderChangeId(),
          change.getTitle(),
          change.getDescription(),
          change.getAuthor(),
          change.getSourceBranch(),
          change.getTargetBranch(),
          change.getLatestCommitSha(),
          change.getStatus().name(),
          change.getCreatedAt(),
          change.getUpdatedAt()
      );
    }
  }

  public record ChangeDetailResponse(
      ChangeSummaryResponse change,
      List<AnalysisRunSummary> analysisRuns,
      RiskAssessmentSummary latestRisk,
      DecisionRecordSummary latestDecision
  ) {
    public static ChangeDetailResponse fromView(ChangeAnalysisView view) {
      List<AnalysisRunSummary> runs = view.runs().stream()
          .map(AnalysisRunSummary::fromDomain)
          .toList();
      RiskAssessmentSummary risk = view.latestRisk() != null
          ? RiskAssessmentSummary.fromDomain(view.latestRisk())
          : null;
      DecisionRecordSummary decision = view.latestDecision() != null
          ? DecisionRecordSummary.fromDomain(view.latestDecision())
          : null;

      return new ChangeDetailResponse(
          ChangeSummaryResponse.fromDomain(view.change()),
          runs,
          risk,
          decision
      );
    }
  }

  public record AnalysisRunSummary(
      UUID id,
      String commitSha,
      String status,
      Instant createdAt,
      Instant completedAt
  ) {
    public static AnalysisRunSummary fromDomain(AnalysisRun run) {
      return new AnalysisRunSummary(
          run.getId().value(),
          run.getCodeSnapshot().commitSha(),
          run.getStatus().name(),
          run.getCreatedAt(),
          run.getCompletedAt().orElse(null)
      );
    }
  }

  public record RiskAssessmentSummary(
      UUID id,
      int overallScore,
      String riskLevel,
      String assessmentVersion
  ) {
    public static RiskAssessmentSummary fromDomain(RiskAssessment risk) {
      return new RiskAssessmentSummary(
          risk.getId().value(),
          risk.getScore().value(),
          risk.getLevel().name(),
          risk.getAssessmentVersion()
      );
    }
  }

  public record DecisionRecordSummary(
      UUID id,
      String outcome,
      String policyVersion,
      boolean overridden
  ) {
    public static DecisionRecordSummary fromDomain(DecisionRecord decision) {
      return new DecisionRecordSummary(
          decision.getId().value(),
          decision.getOutcome().name(),
          decision.getPolicyVersion(),
          decision.getOverride().isPresent()
      );
    }
  }
}