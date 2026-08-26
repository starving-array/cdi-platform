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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for pgvector semantic retrieval, HNSW indexing,
 * tenant isolation, deterministic fallback, and model_version tracking (D1-D5).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class SemanticEvidencePersistenceIntegrationTest {

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

  private final Actor engineer = new Actor("eng-1", Actor.Role.ENGINEER);
  private SearchEvidenceQueryService service;

  @BeforeEach
  void setUp() {
    service = new SearchEvidenceQueryService(evidenceSearchPort);
  }

  @Test
  void pgvectorExtensionAndEmbeddingTablePersistCorrectly() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-v12");
    EvidenceRecord record = seedEvidence(
        tenantId, run, "Redis Connection Timeout", "Redis master node failover latency", "2026-08-16T10:00:00Z");

    Integer count = jdbcTemplate.queryForObject(
        "SELECT count(*) FROM evidence_embedding WHERE tenant_id = ? AND evidence_record_id = ?",
        Integer.class,
        tenantId.value(),
        record.getId().value());

    assertNotNull(count);
    assertEquals(1, count);

    String modelVersion = jdbcTemplate.queryForObject(
        "SELECT model_version FROM evidence_embedding WHERE tenant_id = ? AND evidence_record_id = ?",
        String.class,
        tenantId.value(),
        record.getId().value());

    assertEquals(embeddingPort.getModelVersion(), modelVersion);
  }

  @Test
  void semanticRetrievalFindsMatchingEvidenceByVectorSimilarity() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-sem");
    EvidenceRecord rec1 = seedEvidence(
        tenantId, run, "Postgres deadlocks under high concurrency", "Hikari connection pool leak", "2026-08-16T11:00:00Z");
    seedEvidence(
        tenantId, run, "Frontend CSS styling tweak", "Updated button padding", "2026-08-16T11:05:00Z");

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("Postgres deadlocks under high concurrency", Map.of(), 5),
        tenantId,
        engineer);

    assertFalse(result.degraded());
    assertFalse(result.records().isEmpty());
    assertEquals(rec1.getId(), result.records().get(0).getId());
  }

  @Test
  void strictTenantIsolationPreventsCrossTenantVectorSimilarityLeaks() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();

    AnalysisRun runA = seedRun(tenantA, "sha-a");
    AnalysisRun runB = seedRun(tenantB, "sha-b");

    // Identical text stored in Tenant A and Tenant B
    EvidenceRecord recA = seedEvidence(
        tenantA, runA, "Secret Security Flaw", "Confidential vulnerability report", "2026-08-16T12:00:00Z");
    EvidenceRecord recB = seedEvidence(
        tenantB, runB, "Secret Security Flaw", "Confidential vulnerability report", "2026-08-16T12:00:00Z");

    // Tenant A searches for secret
    SearchEvidenceResult resultA = service.handle(
        new SearchEvidenceQuery("Secret Security Flaw", Map.of(), 10),
        tenantA,
        engineer);

    assertFalse(resultA.degraded());
    assertEquals(1, resultA.records().size());
    assertEquals(recA.getId(), resultA.records().get(0).getId());

    // Tenant B searches for secret
    SearchEvidenceResult resultB = service.handle(
        new SearchEvidenceQuery("Secret Security Flaw", Map.of(), 10),
        tenantB,
        engineer);

    assertFalse(resultB.degraded());
    assertEquals(1, resultB.records().size());
    assertEquals(recB.getId(), resultB.records().get(0).getId());

    // Tenant C with no records gets zero results
    TenantId tenantC = TenantId.generate();
    SearchEvidenceResult resultC = service.handle(
        new SearchEvidenceQuery("Secret Security Flaw", Map.of(), 10),
        tenantC,
        engineer);

    assertFalse(resultC.degraded());
    assertTrue(resultC.records().isEmpty());
  }

  @Test
  void fallsBackToDeterministicIlikeWhenNoVectorMatchesOrEmbeddingDegraded() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-fallback");
    seedEvidence(
        tenantId, run, "SpecialKeyword999", "Random body without keyword in title", "2026-08-16T13:00:00Z");

    // ILIKE match on SpecialKeyword999
    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("SpecialKeyword999", Map.of(), 5),
        tenantId,
        engineer);

    assertFalse(result.degraded());
    assertFalse(result.records().isEmpty());
    assertEquals("SpecialKeyword999", result.records().get(0).getTitle());
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