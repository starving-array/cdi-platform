package com.cdi.application.evidence;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.SearchEvidenceQuery;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end P2 SearchEvidence integration test: real JPA adapters
 * (Testcontainers PostgreSQL) wired into {@link SearchEvidenceQueryService}.
 * Persists the change → analysis_run → evidence_record chain through the
 * repository adapters (UC-18 seeding pattern), then verifies the
 * deterministic SQL/lexical search (ADR-008 B1, T1 title/content mapping), the
 * tenant-scoped isolation, the {@code capturedAt} asc + {@code EvidenceId} UUID
 * tie-breaker ordering (B3), the top-N limit (D3), the default limit (D2), and
 * the ENGINEER-only authorization (use-cases.md §6.2). Each test seeds its own
 * fresh tenant id to stay isolated inside the shared (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class SearchEvidenceQueryServicePersistenceIntegrationTest {

  @Autowired
  private ChangeRepository changeRepository;

  @Autowired
  private AnalysisRunRepository analysisRunRepository;

  @Autowired
  private EvidenceRepository evidenceRepository;

  @Autowired
  private EvidenceSearchPort evidenceSearchPort;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

  private SearchEvidenceQueryService service;

  @BeforeEach
  void setUp() {
    service = new SearchEvidenceQueryService(evidenceSearchPort);
  }

  @Test
  void searchesPersistedEvidenceAcrossTitleAndContent() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    EvidenceRecord kafka = seedEvidence(tenantId, run, "Kafka consumer failure",
        "consumer group lag caused availability impact", "2026-08-15T08:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    seedEvidence(tenantId, run, "Payroll April run", "period-close reconciliation OK",
        "2026-08-15T09:00:00Z", UUID.fromString("00000000-0000-0000-0000-0000000000a2"));

    SearchEvidenceResult titleHit = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), tenantId, engineer);
    assertFalse(titleHit.degraded());
    assertEquals(1, titleHit.records().size());
    assertEquals(kafka.getId(), titleHit.records().get(0).getId());

    SearchEvidenceResult contentHit = service.handle(
        new SearchEvidenceQuery("reconciliation", Map.of()), tenantId, engineer);
    assertFalse(contentHit.degraded());
    assertEquals(1, contentHit.records().size());
    assertEquals("Payroll April run", contentHit.records().get(0).getTitle());
  }

  @Test
  void ordersMatchingRecordsByCapturedAtThenEvidenceId() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    EvidenceRecord third = seedEvidence(tenantId, run, "kafka run c", "kafka",
        "2026-08-15T12:00:00Z", UUID.fromString("00000000-0000-0000-0000-0000000000c3"));
    EvidenceRecord first = seedEvidence(tenantId, run, "kafka run a", "kafka",
        "2026-08-15T08:00:00Z", UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    EvidenceRecord second = seedEvidence(tenantId, run, "kafka run b", "kafka",
        "2026-08-15T08:00:00Z", UUID.fromString("00000000-0000-0000-0000-0000000000c2"));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of(), 20), tenantId, engineer);

    assertFalse(result.degraded());
    assertEquals(List.of(first.getId(), second.getId(), third.getId()),
        result.records().stream().map(EvidenceRecord::getId).toList());
  }

  @Test
  void respectsTopNLimit() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    seedEvidence(tenantId, run, "kafka a", null, "2026-08-15T08:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    seedEvidence(tenantId, run, "kafka b", null, "2026-08-15T09:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    seedEvidence(tenantId, run, "kafka c", null, "2026-08-15T10:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000b3"));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of(), 2), tenantId, engineer);

    assertFalse(result.degraded());
    assertEquals(2, result.records().size());
  }

  @Test
  void returnsEmptyForTenantWithNoMatchingEvidence() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    seedEvidence(tenantId, run, "Payroll", null, "2026-08-15T08:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000d1"));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("postgres", Map.of()), tenantId, engineer);

    assertFalse(result.degraded());
    assertTrue(result.records().isEmpty());
  }

  @Test
  void crossTenantEvidenceIsNotVisible() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    seedEvidence(tenantId, run, "Kafka outage", null, "2026-08-15T08:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000e1"));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), TenantId.generate(), engineer);

    assertFalse(result.degraded());
    assertTrue(result.records().isEmpty());
  }

  @Test
  void systemWorkerRoleRejectedAndTenantAdminAllowed() {
    TenantId tenantId = TenantId.generate();
    AnalysisRun run = seedRun(tenantId, "sha-1");
    seedEvidence(tenantId, run, "Kafka", null, "2026-08-15T08:00:00Z",
        UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    SearchEvidenceResult adminResult = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), tenantId,
        new Actor("admin-1", Actor.Role.TENANT_ADMIN));
    assertFalse(adminResult.degraded());
    assertEquals(1, adminResult.records().size());

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new SearchEvidenceQuery("kafka", Map.of()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());
  }

  private AnalysisRun seedRun(TenantId tenantId, String commitSha) {
    Change change = new Change(
        ChangeId.generate(), RepositoryId.generate(), "PR-" + commitSha,
        "SearchEvidence seed", "seed", "alice", "feature", "main", commitSha,
        Instant.parse("2026-08-15T12:00:00Z"));
    changeRepository.save(tenantId, change);
    return analysisRunRepository.save(tenantId, new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(commitSha, "main"), Instant.parse("2026-08-15T12:00:00Z")));
  }

  private EvidenceRecord seedEvidence(TenantId tenantId, AnalysisRun run, String title,
      String content, String capturedAt, UUID id) {
    EvidenceRecord record = EvidenceRecord.builder()
        .id(new EvidenceId(id))
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