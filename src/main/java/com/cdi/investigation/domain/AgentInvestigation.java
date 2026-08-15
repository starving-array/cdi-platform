package com.cdi.investigation.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.RiskAssessmentId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AgentInvestigation Aggregate Root (domain-model.md §F, use-cases.md §5.3).
 *
 * <p>Represents the AI agent's structured research into a change. It carries
 * {@code InvestigationFinding}s (each grounded by {@link EvidenceCitation}s to
 * real evidence identifiers) that <em>supplement</em> the deterministic
 * {@code RiskAssessment} — the agent never replaces risk scoring and never
 * enforces policy.
 *
 * <p><b>Lifecycle</b> (domain-model.md §C): {@code REQUESTED}
 * → {@code RUNNING} → {@code COMPLETED} | {@code FAILED}. Transitions are
 * guarded domain methods only — no generic setters, no direct status mutation.
 *
 * <p><b>Truth boundary</b>: {@link #complete} accepts only structured,
 * pre-validated findings. A failed investigation is marked {@code FAILED}; it
 * is never silently converted into certainty. The aggregate is immutable after
 * completion/failure (auditability).
 */
public class AgentInvestigation {

  public enum Status {
    REQUESTED, RUNNING, COMPLETED, FAILED
  }

  private final InvestigationId id;
  private final AnalysisRunId analysisRunId;
  private final ChangeId changeId;
  private final RiskAssessmentId riskAssessmentId;

  private Status status;
  private List<InvestigationFinding> findings;
  private InvestigationFailure failure;

  private final Instant createdAt;
  private Instant completedAt;

  public AgentInvestigation(
      InvestigationId id,
      AnalysisRunId analysisRunId,
      ChangeId changeId,
      RiskAssessmentId riskAssessmentId,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "InvestigationId cannot be null");
    this.analysisRunId = Objects.requireNonNull(analysisRunId, "AnalysisRunId cannot be null");
    this.changeId = Objects.requireNonNull(changeId, "ChangeId cannot be null");
    this.riskAssessmentId = Objects.requireNonNull(riskAssessmentId, "RiskAssessmentId cannot be null");
    if (createdAt == null) {
      throw new DomainException("Creation timestamp cannot be null");
    }
    this.status = Status.REQUESTED;
    this.findings = List.of();
    this.createdAt = createdAt;
  }

  public void start() {
    if (this.status != Status.REQUESTED) {
      throw new DomainException("Only REQUESTED investigations can be started");
    }
    this.status = Status.RUNNING;
  }

  public void complete(List<InvestigationFinding> findings, Instant timestamp) {
    if (this.status != Status.RUNNING) {
      throw new DomainException("Only RUNNING investigations can be completed");
    }
    this.findings = findings == null ? List.of() : List.copyOf(findings);
    this.status = Status.COMPLETED;
    this.completedAt = timestamp;
  }

  public void fail(InvestigationFailure failure) {
    if (this.status != Status.RUNNING) {
      throw new DomainException("Only RUNNING investigations can be failed");
    }
    if (failure == null) {
      throw new DomainException("Failure info must be provided");
    }
    this.failure = failure;
    this.status = Status.FAILED;
    this.completedAt = failure.failedAt();
  }

  /**
   * Framework-independent hydration factory for persistence adapters. Performs
   * no state transitions.
   */
  public static AgentInvestigation restore(
      InvestigationId id, AnalysisRunId analysisRunId, ChangeId changeId,
      RiskAssessmentId riskAssessmentId, Status status,
      List<InvestigationFinding> findings, InvestigationFailure failure,
      Instant createdAt, Instant completedAt) {
    AgentInvestigation investigation =
        new AgentInvestigation(id, analysisRunId, changeId, riskAssessmentId, createdAt);
    investigation.status = status;
    investigation.findings = findings == null ? List.of() : List.copyOf(findings);
    investigation.failure = failure;
    investigation.completedAt = completedAt;
    return investigation;
  }

  public InvestigationId getId() { return id; }
  public AnalysisRunId getAnalysisRunId() { return analysisRunId; }
  public ChangeId getChangeId() { return changeId; }
  public RiskAssessmentId getRiskAssessmentId() { return riskAssessmentId; }
  public Status getStatus() { return status; }
  public List<InvestigationFinding> getFindings() { return findings; }
  public Optional<InvestigationFailure> getFailure() { return Optional.ofNullable(failure); }
  public Instant getCreatedAt() { return createdAt; }
  public Optional<Instant> getCompletedAt() { return Optional.ofNullable(completedAt); }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AgentInvestigation that = (AgentInvestigation) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
