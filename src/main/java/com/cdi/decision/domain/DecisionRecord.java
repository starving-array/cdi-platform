package com.cdi.decision.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable DecisionRecord aggregate root.
 */
public class DecisionRecord {
    private final DecisionId id;
    private final TenantId tenantId;
    private final AnalysisRunId analysisRunId;
    private final RiskAssessmentId riskAssessmentId;
    private final PolicyId policyId;
    private final String policyVersion;
    
    private final DecisionOutcome outcome;
    private final List<DecisionReason> reasons;
    private final List<RequiredAction> requiredActions;
    
    private final Instant generatedAt;
    private final HumanOverride override; // Optional

    private DecisionRecord(Builder builder) {
        this.id = builder.id != null ? builder.id : DecisionId.generate();
        this.tenantId = Objects.requireNonNull(builder.tenantId, "TenantId is required");
        this.analysisRunId = Objects.requireNonNull(builder.analysisRunId, "AnalysisRunId is required");
        this.riskAssessmentId = Objects.requireNonNull(builder.riskAssessmentId, "RiskAssessmentId is required");
        this.policyId = Objects.requireNonNull(builder.policyId, "PolicyId is required");
        
        if (builder.policyVersion == null || builder.policyVersion.isBlank()) {
            throw new DomainException("Policy version is required for auditability");
        }
        this.policyVersion = builder.policyVersion;
        
        this.outcome = Objects.requireNonNull(builder.outcome, "Outcome is required");
        this.reasons = List.copyOf(Objects.requireNonNull(builder.reasons, "Reasons list cannot be null"));
        this.requiredActions = List.copyOf(Objects.requireNonNull(builder.requiredActions, "Required actions list cannot be null"));
        this.generatedAt = builder.generatedAt != null ? builder.generatedAt : Instant.now();
        
        this.override = builder.override; // can be null
    }

    public DecisionRecord withOverride(HumanOverride newOverride) {
        if (this.override != null) {
            throw new DomainException("Decision is already overridden");
        }
        Builder b = new Builder()
            .id(this.id)
            .tenantId(this.tenantId)
            .analysisRunId(this.analysisRunId)
            .riskAssessmentId(this.riskAssessmentId)
            .policyId(this.policyId)
            .policyVersion(this.policyVersion)
            .outcome(this.outcome)
            .reasons(this.reasons)
            .requiredActions(this.requiredActions)
            .generatedAt(this.generatedAt)
            .override(newOverride);
        return b.build();
    }

    public DecisionId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public AnalysisRunId getAnalysisRunId() { return analysisRunId; }
    public RiskAssessmentId getRiskAssessmentId() { return riskAssessmentId; }
    public PolicyId getPolicyId() { return policyId; }
    public String getPolicyVersion() { return policyVersion; }
    public DecisionOutcome getOutcome() { 
        return override != null ? override.newOutcome() : outcome; 
    }
    public DecisionOutcome getOriginalOutcome() { return outcome; }
    public List<DecisionReason> getReasons() { return reasons; }
    public List<RequiredAction> getRequiredActions() { return requiredActions; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Optional<HumanOverride> getOverride() { return Optional.ofNullable(override); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private DecisionId id;
        private TenantId tenantId;
        private AnalysisRunId analysisRunId;
        private RiskAssessmentId riskAssessmentId;
        private PolicyId policyId;
        private String policyVersion;
        private DecisionOutcome outcome;
        private List<DecisionReason> reasons;
        private List<RequiredAction> requiredActions;
        private Instant generatedAt;
        private HumanOverride override;

        public Builder id(DecisionId id) { this.id = id; return this; }
        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder analysisRunId(AnalysisRunId analysisRunId) { this.analysisRunId = analysisRunId; return this; }
        public Builder riskAssessmentId(RiskAssessmentId riskAssessmentId) { this.riskAssessmentId = riskAssessmentId; return this; }
        public Builder policyId(PolicyId policyId) { this.policyId = policyId; return this; }
        public Builder policyVersion(String policyVersion) { this.policyVersion = policyVersion; return this; }
        public Builder outcome(DecisionOutcome outcome) { this.outcome = outcome; return this; }
        public Builder reasons(List<DecisionReason> reasons) { this.reasons = reasons; return this; }
        public Builder requiredActions(List<RequiredAction> requiredActions) { this.requiredActions = requiredActions; return this; }
        public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
        public Builder override(HumanOverride override) { this.override = override; return this; }
        public DecisionRecord build() { return new DecisionRecord(this); }
    }
}
