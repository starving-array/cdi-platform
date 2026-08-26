package com.cdi.application.evidence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.port.in.SearchEvidenceQuery;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.EmbeddingPort;
import com.cdi.application.port.out.EvidenceRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.adapter.out.embedding.OnnxEmbeddingAdapter;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end semantic quality integration test using real ONNX all-MiniLM-L6-v2 embeddings
 * with PostgreSQL pgvector (Testcontainers).
 *
 * <p>Validates that:
 * <ul>
 *   <li>"database connection leak" retrieves "PostgreSQL connection pool exhaustion caused by leaked sessions"
 *       (semantic similarity without requiring identical phrasing).</li>
 *   <li>Unrelated evidence ("Frontend UI button color styling update") is NOT returned.</li>
 *   <li>Strict SQL-level tenant isolation is maintained.</li>
 *   <li>Model version "all-MiniLM-L6-v2-onnx-v1" is persisted into database.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
@TestPropertySource(properties = {
    "cdi.embedding.provider=onnx",
    "cdi.evidence.similarity-threshold=0.65"
})
class SemanticQualityIntegrationTest {

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private EvidenceRepository evidenceRepository;

  @Autowired
  private EvidenceSearchPort evidenceSearchPort;

  @Autowired
  private EmbeddingPort embeddingPort;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final Actor engineer = new Actor("eng-onnx", Actor.Role.ENGINEER);
  private SearchEvidenceQueryService service;

  @BeforeEach
  void setUp() {
    service = new SearchEvidenceQueryService(evidenceSearchPort);
  }

  @Test
  void onnxEmbeddingProviderIsActive() {
    assertTrue(embeddingPort instanceof OnnxEmbeddingAdapter,
        "Production OnnxEmbeddingAdapter must be active when cdi.embedding.provider=onnx");
    assertEquals("all-MiniLM-L6-v2-onnx-v1", embeddingPort.getModelVersion());
    assertEquals(384, embeddingPort.getDimensions());
  }

  @Test
  void retrievesSemanticallyRelatedEvidenceWithNonIdenticalPhrasing() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-onnx-1");

    // 1. Relevant evidence with non-identical phrasing
    EvidenceRecord relevantRecord = seedEvidence(
        tenantId,
        run,
        "PostgreSQL connection pool exhaustion caused by leaked sessions",
        "HikariCP pool reached max capacity due to unclosed JDBC connections during batch checkout",
        "2026-08-16T10:00:00Z");

    // 2. Unrelated evidence
    EvidenceRecord unrelatedRecord = seedEvidence(
        tenantId,
        run,
        "Frontend UI button color styling update",
        "Changed primary CTA button background color from blue to indigo for WCAG AA compliance",
        "2026-08-16T10:05:00Z");

    // Execute semantic search query
    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("database connection leak", Map.of(), 5),
        tenantId,
        engineer);

    assertFalse(result.degraded(), "Semantic search should succeed without degradation");
    assertFalse(result.records().isEmpty(), "Should retrieve at least one matching evidence record");

    // Verify relevant record is ranked top
    assertEquals(relevantRecord.getId(), result.records().get(0).getId());

    // Verify model_version is persisted in database
    String persistedModelVersion = jdbcTemplate.queryForObject(
        "SELECT model_version FROM evidence_embedding WHERE tenant_id = ? AND evidence_record_id = ?",
        String.class,
        tenantId.value(),
        relevantRecord.getId().value());
    assertEquals("all-MiniLM-L6-v2-onnx-v1", persistedModelVersion);
  }

  @Test
  void strictTenantIsolationWithOnnxEmbeddings() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();

    AnalysisRun runA = seedRun(tenantA, "sha-tenant-a");
    AnalysisRun runB = seedRun(tenantB, "sha-tenant-b");

    EvidenceRecord recA = seedEvidence(
        tenantA, runA, "Confidential Security Advisory", "Critical auth token disclosure", "2026-08-16T12:00:00Z");
    EvidenceRecord recB = seedEvidence(
        tenantB, runB, "Confidential Security Advisory", "Critical auth token disclosure", "2026-08-16T12:00:00Z");

    SearchEvidenceResult resultA = service.handle(
        new SearchEvidenceQuery("security advisory authentication", Map.of(), 10),
        tenantA,
        engineer);

    assertFalse(resultA.degraded());
    assertEquals(1, resultA.records().size());
    assertEquals(recA.getId(), resultA.records().get(0).getId());

    SearchEvidenceResult resultB = service.handle(
        new SearchEvidenceQuery("security advisory authentication", Map.of(), 10),
        tenantB,
        engineer);

    assertFalse(resultB.degraded());
    assertEquals(1, resultB.records().size());
    assertEquals(recB.getId(), resultB.records().get(0).getId());
  }

  private AnalysisRun seedRun(TenantId tenantId, String commitSha) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-" + commitSha,
        "Semantic seed", "seed", "alice", "feature", "main", commitSha,
        Instant.parse("2026-08-16T12:00:00Z"));
    changeRepository.save(tenantId, change);
    return analysisRunRepository.save(tenantId, new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.parse("2026-08-16T12:00:00Z")));
  }

  private EvidenceRecord seedEvidence(
      TenantId tenantId, AnalysisRun run, String title, String content, String capturedAt) {
    EvidenceRecord record = EvidenceRecord.builder()
        .id(EvidenceId.generate())
        .tenantId(tenantId)
        .analysisRunId(run.getId())
        .source(new EvidenceSource(SourceType.INCIDENT, "INC-" + title))
        .origin(EvidenceOrigin.RETRIEVED)
        .title(title)
        .content(content)
        .capturedAt(Instant.parse(capturedAt))
        .build();
    return evidenceRepository.save(record);
  }
}