package com.cdi.decision.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionRecordTest {

    @Test
    void shouldApplyHumanOverrideImmutably() {
        DecisionRecord original = DecisionRecord.builder()
                .tenantId(TenantId.generate())
                .analysisRunId(AnalysisRunId.generate())
                .riskAssessmentId(RiskAssessmentId.generate())
                .policyId(PolicyId.generate())
                .policyVersion("v1")
                .outcome(DecisionOutcome.BLOCK)
                .reasons(List.of(new DecisionReason("Failed", "R1", List.of())))
                .requiredActions(List.of())
                .build();
                
        HumanOverride override = new HumanOverride("actor-123", DecisionOutcome.BLOCK, DecisionOutcome.APPROVE, "Emergency fix", Instant.now());
        
        DecisionRecord updated = original.withOverride(override);
        
        assertEquals(DecisionOutcome.BLOCK, original.getOutcome()); // original unchanged
        assertEquals(DecisionOutcome.APPROVE, updated.getOutcome());
        assertEquals(DecisionOutcome.BLOCK, updated.getOriginalOutcome());
        assertEquals("actor-123", updated.getOverride().get().actorId());
    }

    @Test
    void shouldNotAllowDoubleOverride() {
        DecisionRecord original = DecisionRecord.builder()
                .tenantId(TenantId.generate())
                .analysisRunId(AnalysisRunId.generate())
                .riskAssessmentId(RiskAssessmentId.generate())
                .policyId(PolicyId.generate())
                .policyVersion("v1")
                .outcome(DecisionOutcome.BLOCK)
                .reasons(List.of(new DecisionReason("Failed", "R1", List.of())))
                .requiredActions(List.of())
                .build();
                
        HumanOverride override1 = new HumanOverride("actor-1", DecisionOutcome.BLOCK, DecisionOutcome.APPROVE, "First", Instant.now());
        HumanOverride override2 = new HumanOverride("actor-2", DecisionOutcome.APPROVE, DecisionOutcome.BLOCK, "Second", Instant.now());
        
        DecisionRecord overridden = original.withOverride(override1);
        
        assertThrows(DomainException.class, () -> overridden.withOverride(override2));
    }
}
