package com.cdi.risk.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.RiskAssessmentId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Risk Assessment aggregate root.
 * Represents an objective evaluation of engineering risk for a specific analysis run.
 */
public class RiskAssessment {
    
    private final RiskAssessmentId id;
    private final AnalysisRunId analysisRunId;
    
    private final RiskScore score;
    private final RiskLevel level;
    private final List<RiskFactor> factors;
    
    private final EvidenceState evidenceState;
    private final String assessmentVersion;
    private final Instant calculatedAt;

    public RiskAssessment(
            RiskAssessmentId id,
            AnalysisRunId analysisRunId,
            RiskScore score,
            RiskLevel level,
            List<RiskFactor> factors,
            EvidenceState evidenceState,
            String assessmentVersion,
            Instant calculatedAt) {
            
        this.id = Objects.requireNonNull(id, "RiskAssessmentId cannot be null");
        this.analysisRunId = Objects.requireNonNull(analysisRunId, "AnalysisRunId cannot be null");
        this.score = Objects.requireNonNull(score, "RiskScore cannot be null");
        this.level = Objects.requireNonNull(level, "RiskLevel cannot be null");
        this.factors = List.copyOf(Objects.requireNonNull(factors, "Factors cannot be null"));
        this.evidenceState = Objects.requireNonNull(evidenceState, "EvidenceState cannot be null");
        
        if (assessmentVersion == null || assessmentVersion.isBlank()) {
            throw new DomainException("Assessment version cannot be blank");
        }
        this.assessmentVersion = assessmentVersion;
        this.calculatedAt = Objects.requireNonNull(calculatedAt, "Calculated timestamp cannot be null");
    }

    public RiskAssessmentId getId() { return id; }
    public AnalysisRunId getAnalysisRunId() { return analysisRunId; }
    public RiskScore getScore() { return score; }
    public RiskLevel getLevel() { return level; }
    public List<RiskFactor> getFactors() { return factors; }
    public EvidenceState getEvidenceState() { return evidenceState; }
    public String getAssessmentVersion() { return assessmentVersion; }
    public Instant getCalculatedAt() { return calculatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskAssessment that = (RiskAssessment) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
