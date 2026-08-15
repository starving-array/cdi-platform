package com.cdi.policy.domain;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import com.cdi.systemcontext.domain.CriticalityTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEngineTest {

    private PolicyEngine engine;
    private Instant now;
    private TenantId tenantId;
    
    @BeforeEach
    void setUp() {
        engine = new PolicyEngine();
        now = Instant.now();
        tenantId = TenantId.generate();
    }

    @Test
    void shouldEvaluateToBlockWhenMultipleRulesMatchAndBlockIsHighestPrecedence() {
        PolicyRule ruleApprove = new PolicyRule("RULE_1", Set.of(CriticalityTier.TIER_0), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Approve T0");
        PolicyRule ruleReview = new PolicyRule("RULE_2", Set.of(), Set.of(RiskLevel.HIGH), Set.of(), DecisionOutcome.REVIEW_REQUIRED, List.of(RequiredAction.HUMAN_REVIEW), "Review HIGH");
        PolicyRule ruleBlock = new PolicyRule("RULE_3", Set.of(CriticalityTier.TIER_0), Set.of(RiskLevel.HIGH), Set.of(), DecisionOutcome.BLOCK, List.of(), "Block T0+HIGH");
        
        Policy policy = new Policy(PolicyId.generate(), tenantId, "Test Policy", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(ruleApprove, ruleReview, ruleBlock), now);
        
        RiskAssessment assessment = new RiskAssessment(RiskAssessmentId.generate(), AnalysisRunId.generate(), RiskScore.of(75), RiskLevel.HIGH, List.of(), EvidenceState.EVIDENCE_AVAILABLE, "v1", now);
        
        DecisionRecord decision = engine.evaluate(policy, assessment, CriticalityTier.TIER_0, now);
        
        assertEquals(DecisionOutcome.BLOCK, decision.getOutcome());
        assertEquals(3, decision.getReasons().size());
    }

    @Test
    void shouldFallbackToReviewRequiredWhenNoRulesMatch() {
        PolicyRule rule = new PolicyRule("RULE_1", Set.of(CriticalityTier.TIER_0), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Approve T0");
        Policy policy = new Policy(PolicyId.generate(), tenantId, "Test Policy", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), now);
        
        RiskAssessment assessment = new RiskAssessment(RiskAssessmentId.generate(), AnalysisRunId.generate(), RiskScore.of(30), RiskLevel.LOW, List.of(), EvidenceState.EVIDENCE_AVAILABLE, "v1", now);
        
        // Passing TIER_1, so it shouldn't match RULE_1
        DecisionRecord decision = engine.evaluate(policy, assessment, CriticalityTier.TIER_1, now);
        
        assertEquals(DecisionOutcome.REVIEW_REQUIRED, decision.getOutcome());
        assertEquals("DEFAULT_FALLBACK", decision.getReasons().get(0).ruleId());
    }
    
    @Test
    void shouldPreserveExactPolicyVersion() {
        PolicyRule rule = new PolicyRule("RULE_1", Set.of(), Set.of(), Set.of(), DecisionOutcome.APPROVE, List.of(), "Approve All");
        Policy policy = new Policy(PolicyId.generate(), tenantId, "Test Policy", "", PolicyStatus.ACTIVE, PolicyVersion.of("v1.5.0"), List.of(rule), now);
        
        RiskAssessment assessment = new RiskAssessment(RiskAssessmentId.generate(), AnalysisRunId.generate(), RiskScore.of(30), RiskLevel.LOW, List.of(), EvidenceState.EVIDENCE_AVAILABLE, "v1", now);
        DecisionRecord decision = engine.evaluate(policy, assessment, CriticalityTier.TIER_3, now);
        
        assertEquals("v1.5.0", decision.getPolicyVersion());
    }
}
