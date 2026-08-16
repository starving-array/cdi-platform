package com.cdi.evidence.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository over the {@code evidence_record} table.
 *
 * <p>Deterministic-first search (ADR-008 B1, V10): a tenant-scoped SQL
 * {@code ILIKE} containment scan over {@code title}/{@code content}
 * (ports-and-adapters.md §2.3 "SQL ILIKE"), ordered by {@code captured_at}
 * ascending with {@code id} (the {@code EvidenceId} UUID) as the deterministic
 * tie-breaker (ADR-008 B3), limited to the caller's top-N. This is the
 * deterministic future-facing implementation behind the same
 * {@code EvidenceSearchPort} that will later host semantic/vector retrieval
 * (no pgvector here — V10 deliberately adds no semantic infrastructure).
 */
public interface EvidenceJpaRepository extends JpaRepository<EvidenceEntity, UUID> {

  /**
   * Deterministic SQL/lexical search (ADR-008 B1): returns evidence rows of the
   * given tenant whose {@code title} or {@code content} contains the query
   * pattern (case-insensitive {@code ILIKE}). Content {@code NULL} never
   * matches, matching rows are ordered {@code captured_at} ascending with the
   * evidence id as tie-breaker (B3), and at most {@code limit} rows are
   * returned (top-N, D3 — no pagination semantics).
   */
  @Query(value = "SELECT * FROM evidence_record "
      + "WHERE tenant_id = :tenantId "
      + "AND (title ILIKE :pattern OR content ILIKE :pattern) "
      + "ORDER BY captured_at ASC, id ASC "
      + "LIMIT :limit",
      nativeQuery = true)
  List<EvidenceEntity> searchByQuery(
      @Param("tenantId") UUID tenantId,
      @Param("pattern") String pattern,
      @Param("limit") int limit);
}