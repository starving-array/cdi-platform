package com.cdi.application.analysis;

import com.cdi.application.port.out.AgentContext;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.risk.domain.DeterministicRiskEngine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class LlmAgentPortAdapterTest {

    static class TestLlmClient implements LlmClient {
        String lastPrompt;
        @Override
        public String invoke(String prompt) {
            this.lastPrompt = prompt;
            return "FINDING\nsummary: test\nexplanation: test\nconfidence: 0.9";
        }
    }

    @Test
    void testPromptBoundsAndRedaction() {
        TestLlmClient client = new TestLlmClient();
        String secretApiKey = "super-secret-api-key-12345";
        LlmConfig config = new LlmConfig(secretApiKey, "dummy", Duration.ofSeconds(10), "dummy");
        LlmAgentPortAdapter adapter = new LlmAgentPortAdapter(client, config);
        
        AgentContext ctx = new AgentContext(TenantId.generate(), ChangeId.generate(), AnalysisRunId.generate(), "commit123");
        RiskAssessment risk = new RiskAssessment(
            RiskAssessmentId.generate(), AnalysisRunId.generate(),
            RiskScore.of(50), RiskLevel.MEDIUM, List.of(), EvidenceState.EVIDENCE_AVAILABLE,
            DeterministicRiskEngine.RULE_VERSION, Instant.now()
        );
        
        EvidenceRecord rec1 = EvidenceRecord.builder()
            .id(EvidenceId.generate())
            .tenantId(TenantId.generate())
            .analysisRunId(AnalysisRunId.generate())
            .origin(com.cdi.evidence.domain.EvidenceOrigin.AGENT_DISCOVERED)
            .source(new com.cdi.evidence.domain.EvidenceSource(com.cdi.evidence.domain.SourceType.INCIDENT, "INC-100"))
            .title("Title with api_key = \"secret123\"")
            .build();
            
        adapter.investigate(ctx, risk, List.of(rec1));
        
        String prompt = client.lastPrompt;
        assertTrue(prompt.contains("--- BEGIN UNTRUSTED REPOSITORY EVIDENCE ---"));
        assertTrue(prompt.contains("--- END UNTRUSTED REPOSITORY EVIDENCE ---"));
        assertTrue(prompt.contains("REDACTED"));
        assertFalse(prompt.contains("secret123"));
        assertFalse(prompt.contains(secretApiKey));
    }
    
    @Test
    void testCodeIntelligenceFieldRedaction() {
        TestLlmClient client = new TestLlmClient();
        LlmAgentPortAdapter adapter = new LlmAgentPortAdapter(client, new LlmConfig("dummy", "dummy", Duration.ofSeconds(10), "dummy"));
        
        AgentContext ctx = new AgentContext(TenantId.generate(), ChangeId.generate(), AnalysisRunId.generate(), "commit123");
        RiskAssessment risk = new RiskAssessment(
            RiskAssessmentId.generate(), AnalysisRunId.generate(),
            RiskScore.of(50), RiskLevel.MEDIUM, List.of(), EvidenceState.EVIDENCE_AVAILABLE,
            DeterministicRiskEngine.RULE_VERSION, Instant.now()
        );
        
        InvestigationCodeIntelligence intel = new InvestigationCodeIntelligence(
            "commit123",
            List.of(),
            List.of("void init(String access_token = \"my_super_secret_token_123\")"),
            java.util.Set.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            java.util.Set.of()
        );
        adapter.setCodeIntelligence(intel);
            
        adapter.investigate(ctx, risk, List.of());
        
        String prompt = client.lastPrompt;
        assertTrue(prompt.contains("REDACTED"));
        assertFalse(prompt.contains("my_super_secret_token_123"));
    }
    
    @Test
    void ordinaryCodeNotRedacted() {
        TestLlmClient client = new TestLlmClient();
        LlmAgentPortAdapter adapter = new LlmAgentPortAdapter(client, new LlmConfig("dummy", "dummy", Duration.ofSeconds(10), "dummy"));
        
        AgentContext ctx = new AgentContext(TenantId.generate(), ChangeId.generate(), AnalysisRunId.generate(), "commit123");
        RiskAssessment risk = new RiskAssessment(
            RiskAssessmentId.generate(), AnalysisRunId.generate(),
            RiskScore.of(50), RiskLevel.MEDIUM, List.of(), EvidenceState.EVIDENCE_AVAILABLE,
            DeterministicRiskEngine.RULE_VERSION, Instant.now()
        );
        
        EvidenceRecord rec1 = EvidenceRecord.builder()
            .id(EvidenceId.generate())
            .tenantId(TenantId.generate())
            .analysisRunId(AnalysisRunId.generate())
            .origin(com.cdi.evidence.domain.EvidenceOrigin.AGENT_DISCOVERED)
            .source(new com.cdi.evidence.domain.EvidenceSource(com.cdi.evidence.domain.SourceType.INCIDENT, "INC-100"))
            .title("String token = createToken();")
            .build();
            
        adapter.investigate(ctx, risk, List.of(rec1));
        
        String prompt = client.lastPrompt;
        assertTrue(prompt.contains("String token = createToken();"));
    }
    
    @Test
    void testOversizedPromptBoundedAndPreservesPostamble() {
        TestLlmClient client = new TestLlmClient();
        LlmAgentPortAdapter adapter = new LlmAgentPortAdapter(client, new LlmConfig("dummy", "dummy", Duration.ofSeconds(10), "dummy"));
        
        AgentContext ctx = new AgentContext(TenantId.generate(), ChangeId.generate(), AnalysisRunId.generate(), "commit123");
        RiskAssessment risk = new RiskAssessment(
            RiskAssessmentId.generate(), AnalysisRunId.generate(),
            RiskScore.of(50), RiskLevel.MEDIUM, List.of(), EvidenceState.EVIDENCE_AVAILABLE,
            DeterministicRiskEngine.RULE_VERSION, Instant.now()
        );
        
        String giantTitle = "A".repeat(60000);
        
        EvidenceRecord rec1 = EvidenceRecord.builder()
            .id(EvidenceId.generate())
            .tenantId(TenantId.generate())
            .analysisRunId(AnalysisRunId.generate())
            .origin(com.cdi.evidence.domain.EvidenceOrigin.AGENT_DISCOVERED)
            .source(new com.cdi.evidence.domain.EvidenceSource(com.cdi.evidence.domain.SourceType.INCIDENT, "INC-100"))
            .title(giantTitle)
            .build();
            
        adapter.investigate(ctx, risk, List.of(rec1));
        
        String prompt = client.lastPrompt;
        assertTrue(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 50000);
        assertTrue(prompt.contains("...[TRUNCATED]"));
        assertTrue(prompt.contains("--- END UNTRUSTED REPOSITORY EVIDENCE ---"));
        assertTrue(prompt.contains("Please provide investigation findings"));
        assertTrue(prompt.endsWith("---END PROMPT---"));
    }
}
