package com.cdi.application.analysis;

import com.cdi.analysis.domain.FileDiff;
import com.cdi.analysis.domain.CodeContext;
import com.cdi.analysis.domain.parsing.JavaChangeAnalyzer;
import com.cdi.analysis.domain.parsing.JavaDependencyAnalyzer;
import com.cdi.analysis.domain.parsing.ImpactGraph;
import com.cdi.analysis.domain.parsing.ImpactGraphResult;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ImpactGraphDownstreamIntegrationTest {

    private final TenantId tenantId = TenantId.generate();
    private final RepositoryId repoId = RepositoryId.generate();
    private final String SHA = "abc123def456";

    private class TestSourceControlPort implements SourceControlPort {
        public List<String> listFiles(TenantId t, RepositoryId r, String sha) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < 70; i++) {
                list.add("src/main/java/com/cdi/Padding" + i + ".java");
            }
            list.add("src/main/java/com/cdi/PaymentController.java");
            list.add("src/main/java/com/cdi/PaymentService.java");
            return list;
        }

        public byte[] getFileContent(TenantId t, RepositoryId r, String path, String sha) {
            if (path.contains("PaymentService")) {
                return "package com.cdi;\npublic class PaymentService {\n  public void processPayment() {}\n}".getBytes(StandardCharsets.UTF_8);
            }
            if (path.contains("PaymentController")) {
                return "package com.cdi;\npublic class PaymentController {\n  public void createPayment() {\n    new PaymentService().processPayment();\n  }\n}".getBytes(StandardCharsets.UTF_8);
            }
            return "package com.cdi;\npublic class Padding {}".getBytes(StandardCharsets.UTF_8);
        }
        public void publishStatusCheck(TenantId t, RepositoryId r, String sha, com.cdi.decision.domain.DecisionOutcome o, java.util.List<com.cdi.decision.domain.DecisionReason> rs, String url) {}
        public java.util.List<FileDiff> getDiff(TenantId t, RepositoryId r, String sha) { return List.of(); }
        public com.cdi.application.port.out.ChangeMetadata getChangeMetadata(TenantId t, RepositoryId r, String sha) { return null; }
    }

    @Test
    public void testCandidateOutsideFirst50() {
        TestSourceControlPort port = new TestSourceControlPort();
        CodeContextAssembler assembler = new CodeContextAssembler(port);
        
        List<FileDiff> diff = List.of(new FileDiff("src/main/java/com/cdi/PaymentService.java", 1, 0, FileDiff.ChangeType.MODIFIED));
        CodeContext ctx = assembler.assemble(tenantId, repoId, SHA, diff, 2);
        
        assertEquals(com.cdi.analysis.domain.CoverageState.FULL, ctx.coverageState());
        
        var depAnalysis = new JavaDependencyAnalyzer().analyze(ctx);
        ImpactGraphResult graph = new ImpactGraph(depAnalysis).build();
        
        assertTrue(graph.summary.directlyAffectedMembers() > 0, "Should have discovered downstream controller");
        
        boolean foundCaller = graph.edges.stream().anyMatch(e -> 
            e.edgeType().equals("CALLED_BY") && e.sourceFile().contains("PaymentController")
        );
        assertTrue(foundCaller, "PaymentController should be found as caller");
    }
}
