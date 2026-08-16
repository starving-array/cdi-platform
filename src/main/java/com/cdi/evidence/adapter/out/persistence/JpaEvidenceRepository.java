package com.cdi.evidence.adapter.out.persistence;

import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.port.out.EvidenceRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA/PostgreSQL adapter backing both the deterministic evidence store
 * ({@code EvidenceRepository.save}, for seeding and the future ingestion use
 * case) and the deterministic-first {@code EvidenceSearchPort.searchByQuery}
 * (P2 SearchEvidence, ADR-008). Persistence-side only: no domain rules here.
 *
 * <p><b>Deterministic-first search (ADR-008 B1)</b>: {@link #searchByQuery}
 * runs a tenant-scoped SQL {@code ILIKE} containment scan over
 * {@code title}/{@code content} ({@code EvidenceJpaRepository.searchByQuery}),
 * ordered {@code captured_at} asc with the {@code EvidenceId} UUID as
 * tie-breaker (B3), top-N (D3 — no pagination). Semantic/vector retrieval is
 * deliberately NOT implemented here (V10 adds no pgvector infrastructure); the
 * two worker-oriented methods {@code searchSimilarChanges} /
 * {@code searchIncidents} remain unused by the deterministic store and return
 * empty (deferred with semantic search, application-layer.md §13.5), so any
 * accidental caller degrades to zero evidence per the EVIDENCE_SEARCH failure
 * policy (ports-and-adapters.md §4) rather than crashing.
 *
 * <p><b>Failure policy (ADR-008 B4)</b>: any persistence/search failure inside
 * {@link EvidenceSearchPort} methods is translated into a non-retryable
 * {@code PortException(EVIDENCE_SEARCH, ...)}; the SearchEvidence query
 * service maps that into a {@code degraded} result (never a raised
 * {@code ApplicationError}). No retry is performed here.
 */
@Repository
public class JpaEvidenceRepository implements EvidenceRepository, EvidenceSearchPort {

  private final EvidenceJpaRepository evidenceJpaRepository;

  public JpaEvidenceRepository(EvidenceJpaRepository evidenceJpaRepository) {
    this.evidenceJpaRepository = evidenceJpaRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
    try {
      return evidenceJpaRepository
          .searchByQuery(tenantId.value(), wrapLike(query), limit)
          .stream()
          .map(EvidenceMapper::toDomain)
          .toList();
    } catch (RuntimeException e) {
      throw new PortException(PortType.EVIDENCE_SEARCH, false,
          "Deterministic evidence search failed", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchSimilarChanges(
      TenantId tenantId, List<String> filePaths, int limit) {
    // Deferred: semantic/vector similar-change retrieval is not implemented by
    // the deterministic store (ADR-008 B1, application-layer.md §13.5).
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchIncidents(
      TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit) {
    // Deferred: incident-index retrieval is not implemented by the
    // deterministic store (ADR-008 B1, application-layer.md §13.5).
    return List.of();
  }

  @Override
  @Transactional
  public EvidenceRecord save(EvidenceRecord record) {
    EvidenceEntity entity = EvidenceMapper.toEntity(record);
    return EvidenceMapper.toDomain(evidenceJpaRepository.save(entity));
  }

  private static String wrapLike(String query) {
    return "%" + query + "%";
  }
}