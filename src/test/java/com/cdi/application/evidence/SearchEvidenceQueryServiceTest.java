package com.cdi.application.evidence;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.port.in.SearchEvidenceQuery;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceOrigin;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.evidence.domain.EvidenceSource;
import com.cdi.evidence.domain.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the P2 SearchEvidence query service (ADR-008). Uses a fake
 * {@code EvidenceSearchPort} that emulates the deterministic SQL/lexical
 * search contract (tenant-scoped, title/content substring, ordered
 * {@code capturedAt} asc then {@code EvidenceId} UUID asc, top-N limit) and
 * can be switched into synchronous failure mode to prove the degraded result
 * contract (D5, B4).
 */
class SearchEvidenceQueryServiceTest {

  private static final Instant EARLY = Instant.parse("2026-08-15T08:00:00Z");
  private static final Instant LATER = Instant.parse("2026-08-15T12:00:00Z");

  private final TenantId tenantId = TenantId.generate();
  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);

  private FakeEvidenceSearchPort evidenceSearchPort;
  private SearchEvidenceQueryService service;

  @BeforeEach
  void setUp() {
    evidenceSearchPort = new FakeEvidenceSearchPort();
    service = new SearchEvidenceQueryService(evidenceSearchPort);
  }

  @Test
  void shouldReturnMatchedRecordsForEngineerWithoutDegradation() {
    evidenceSearchPort.records = List.of(
        record(tenantId, "Kafka outage", "consumer lag", EARLY, 2),
        record(tenantId, "Payroll fix", "unrelated", LATER, 1));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of(), 20), tenantId, engineer);

    assertFalse(result.degraded());
    assertEquals(1, result.records().size());
    assertEquals("Kafka outage", result.records().get(0).getTitle());
  }

  @Test
  void shouldMatchContentFieldAsWellAsTitle() {
    evidenceSearchPort.records = List.of(
        record(tenantId, "Deployment", "postgres connection pool exhausted", EARLY, 1));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("connection pool", Map.of()), tenantId, engineer);

    assertFalse(result.degraded());
    assertEquals(1, result.records().size());
  }

  @Test
  void shouldRejectSystemWorkerAndAllowTenantAdmin() {
    TenantId otherTenant = TenantId.generate();
    evidenceSearchPort.records = List.of(record(tenantId, "Kafka", null, EARLY, 1));

    SearchEvidenceResult adminResult = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), tenantId,
        new Actor("admin-1", Actor.Role.TENANT_ADMIN));
    assertFalse(adminResult.degraded());
    assertEquals(1, adminResult.records().size());

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new SearchEvidenceQuery("kafka", Map.of()),
            otherTenant, new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());
  }

  @Test
  void shouldDelegateEffectiveTenantQueryAndLimitToPort() {
    service.handle(new SearchEvidenceQuery("kafka", Map.of(), 11), tenantId, engineer);

    assertEquals(tenantId, evidenceSearchPort.lastTenantId);
    assertEquals("kafka", evidenceSearchPort.lastQuery);
    assertEquals(11, evidenceSearchPort.lastLimit);
  }

  @Test
  void shouldDefaultLimitToTwenty() {
    service.handle(new SearchEvidenceQuery("kafka", Map.of()), tenantId, engineer);

    assertEquals(SearchEvidenceQuery.DEFAULT_LIMIT, evidenceSearchPort.lastLimit);
    assertEquals(20, evidenceSearchPort.lastLimit);
  }

  @Test
  void shouldRespectCallerOverriddenLimit() {
    service.handle(new SearchEvidenceQuery("kafka", Map.of(), 3), tenantId, engineer);

    assertEquals(3, evidenceSearchPort.lastLimit);
  }

  @Test
  void shouldEnforceTopNLimit() {
    evidenceSearchPort.records = List.of(
        record(tenantId, "kafka one", null, EARLY, 1),
        record(tenantId, "kafka two", null, LATER, 2),
        record(tenantId, "kafka three", null, LATER.plusSeconds(1), 3));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of(), 2), tenantId, engineer);

    assertEquals(2, result.records().size());
  }

  @Test
  void shouldPreserveDeterministicOrdering() {
    evidenceSearchPort.records = List.of(
        record(tenantId, "kafka gen", null, LATER, 3),
        record(tenantId, "kafka earliest", null, EARLY, 1),
        record(tenantId, "kafka tie", null, EARLY, 2));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of(), 20), tenantId, engineer);

    assertEquals(List.of("kafka earliest", "kafka tie", "kafka gen"),
        result.records().stream().map(EvidenceRecord::getTitle).toList());
  }

  @Test
  void shouldReturnEmptyResultWithoutDegradationWhenNothingMatches() {
    evidenceSearchPort.records = List.of(record(tenantId, "Payroll", null, EARLY, 1));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), tenantId, engineer);

    assertFalse(result.degraded());
    assertTrue(result.records().isEmpty());
  }

  @Test
  void shouldDegradeWithZeroRecordsOnSynchronousSearchFailure() {
    evidenceSearchPort.failSearch = true;

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), tenantId, engineer);

    assertTrue(result.degraded());
    assertTrue(result.records().isEmpty());
  }

  @Test
  void shouldIsolateOtherTenantsEvidence() {
    TenantId otherTenant = TenantId.generate();
    evidenceSearchPort.records = List.of(record(otherTenant, "Kafka", null, EARLY, 1));

    SearchEvidenceResult result = service.handle(
        new SearchEvidenceQuery("kafka", Map.of()), TenantId.generate(), engineer);

    assertFalse(result.degraded());
    assertTrue(result.records().isEmpty());
  }

  private EvidenceRecord record(TenantId tenantId, String title, String content,
      Instant capturedAt, int idSuffix) {
    return EvidenceRecord.builder()
        .id(new EvidenceId(UUID.fromString(
            "00000000-0000-0000-0000-00000000000" + idSuffix)))
        .tenantId(tenantId)
        .analysisRunId(AnalysisRunId.generate())
        .source(new EvidenceSource(SourceType.INCIDENT, "INC-" + idSuffix))
        .origin(EvidenceOrigin.RETRIEVED)
        .title(title)
        .content(content)
        .capturedAt(capturedAt)
        .build();
  }

  private static class FakeEvidenceSearchPort implements EvidenceSearchPort {

    List<EvidenceRecord> records = List.of();
    boolean failSearch;
    TenantId lastTenantId;
    String lastQuery;
    int lastLimit;

    @Override
    public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
      lastTenantId = tenantId;
      lastQuery = query;
      lastLimit = limit;
      if (failSearch) {
        throw new PortException(PortType.EVIDENCE_SEARCH, false, "evidence store down");
      }
      return records.stream()
          .filter(r -> r.getTenantId().equals(tenantId))
          .filter(r -> matches(r, query))
          .sorted(Comparator
              .comparing(EvidenceRecord::getCapturedAt)
              .thenComparing(r -> r.getId().value()))
          .limit(Math.max(0, limit))
          .toList();
    }

    @Override
    public List<EvidenceRecord> searchSimilarChanges(
        TenantId tenantId, List<String> filePaths, int limit) {
      return List.of();
    }

    @Override
    public List<EvidenceRecord> searchIncidents(
        TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit) {
      return List.of();
    }

    private boolean matches(EvidenceRecord record, String query) {
      String q = query.toLowerCase();
      boolean inTitle = record.getTitle().toLowerCase().contains(q);
      boolean inContent = record.getContent().map(c -> c.toLowerCase().contains(q)).orElse(false);
      return inTitle || inContent;
    }
  }
}