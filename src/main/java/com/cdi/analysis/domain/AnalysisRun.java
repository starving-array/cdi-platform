package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * AnalysisRun Aggregate Root.
 * Represents the evaluation of one exact code snapshot. 
 * This is isolated from the Change aggregate to ensure historical auditability.
 */
public class AnalysisRun {

    public enum Status {
        QUEUED, RUNNING, COMPLETED, FAILED, SUPERSEDED
    }

    private final AnalysisRunId id;
    private final ChangeId changeId;
    private final CodeSnapshot codeSnapshot;
    
    private Status status;
    private AnalysisFailure failureInfo;
    
    private final Instant createdAt;
    private Instant completedAt;

    public AnalysisRun(AnalysisRunId id, ChangeId changeId, CodeSnapshot codeSnapshot, Instant createdAt) {
        if (id == null) throw new DomainException("AnalysisRun ID cannot be null");
        if (changeId == null) throw new DomainException("Change ID cannot be null");
        if (codeSnapshot == null) throw new DomainException("CodeSnapshot cannot be null");
        if (createdAt == null) throw new DomainException("Creation timestamp cannot be null");

        this.id = id;
        this.changeId = changeId;
        this.codeSnapshot = codeSnapshot;
        this.status = Status.QUEUED;
        this.createdAt = createdAt;
    }

    public void start() {
        if (this.status != Status.QUEUED) {
            throw new DomainException("Only QUEUED analyses can be started");
        }
        this.status = Status.RUNNING;
    }

    public void complete(Instant timestamp) {
        if (this.status != Status.RUNNING) {
            throw new DomainException("Only RUNNING analyses can be completed");
        }
        this.status = Status.COMPLETED;
        this.completedAt = timestamp;
    }

    public void fail(AnalysisFailure failure) {
        if (this.status != Status.QUEUED && this.status != Status.RUNNING) {
            throw new DomainException("Cannot fail an analysis that is already completed or superseded");
        }
        if (failure == null) {
            throw new DomainException("Failure info must be provided");
        }
        this.status = Status.FAILED;
        this.failureInfo = failure;
        this.completedAt = failure.failedAt();
    }

    public void supersede() {
        if (this.status == Status.COMPLETED || this.status == Status.FAILED) {
            throw new DomainException("Completed or failed analyses cannot be superseded, they remain as historical records");
        }
        if (this.status == Status.SUPERSEDED) {
            throw new DomainException("Analysis is already superseded");
        }
        this.status = Status.SUPERSEDED;
    }

    public void retry() {
        if (this.status != Status.FAILED) {
            throw new DomainException("Only FAILED analyses can be retried");
        }
        this.status = Status.QUEUED;
        this.failureInfo = null;
        this.completedAt = null;
    }

    /**
     * Reconstructs an AnalysisRun from its persisted state.
     *
     * <p>Framework-independent hydration factory used by persistence adapters
     * to rebuild the aggregate exactly as stored: status, failure info, and
     * completion time are supplied explicitly rather than defaulted by the
     * constructor. This is a persistence-read helper; it performs no state
     * transitions.
     */
    public static AnalysisRun restore(AnalysisRunId id, ChangeId changeId,
            CodeSnapshot codeSnapshot, Status status,
            AnalysisFailure failureInfo, Instant createdAt, Instant completedAt) {
        AnalysisRun run = new AnalysisRun(id, changeId, codeSnapshot, createdAt);
        run.status = status;
        run.failureInfo = failureInfo;
        run.completedAt = completedAt;
        return run;
    }

    // Getters
    public AnalysisRunId getId() { return id; }
    public ChangeId getChangeId() { return changeId; }
    public CodeSnapshot getCodeSnapshot() { return codeSnapshot; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Optional<Instant> getCompletedAt() { return Optional.ofNullable(completedAt); }
    public Optional<AnalysisFailure> getFailureInfo() { return Optional.ofNullable(failureInfo); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisRun that = (AnalysisRun) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
