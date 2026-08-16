package com.cdi.application.port.out;

import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;

import java.util.List;

/**
 * Outbound port retrieving historical truth — past changes and incidents —
 * relevant to the current change (ports-and-adapters.md §2.3,
 * application-layer.md §7).
 *
 * <p>The application layer does not know whether the adapter uses SQL
 * {@code ILIKE}, pgvector semantic search, or an external vector store.
 *
 * <p><b>Failure policy:</b> no retry; the worker gracefully degrades to zero
 * evidence and the risk assessment reflects the missing evidence state via
 * deterministic signals only.
 *
 * <p><b>P2 SearchEvidence</b> (use-cases.md §6.2, ADR-008): the new
 * {@link #searchByQuery} is the query-first, deterministic (SQL {@code ILIKE})
 * evidence search endpoint of the first P2 capability. The application-layer
 * "queries never call external ports" rule (application-layer.md §6) is amended
 * for this single read-only call — a sanctioned exception grounded in the
 * port's degradation contract (ports-and-adapters.md §4). The pre-existing
 * worker-oriented methods remain untouched.
 */
public interface EvidenceSearchPort {

  List<EvidenceRecord> searchSimilarChanges(TenantId tenantId, List<String> filePaths, int limit);

  List<EvidenceRecord> searchIncidents(TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit);

  /**
   * Query-based evidence search (P2 SearchEvidence, use-cases.md §6.2, ADR-008).
   *
   * <p>Deterministic-first (B1): a tenant-scoped SQL {@code ILIKE} containment
   * scan over {@code EvidenceRecord} {@code title}/{@code content}. Semantic/
   * vector retrieval stays deferred behind this same method (application-layer.md
   * §13.5). Results are ordered {@code capturedAt} ascending with the
   * {@code EvidenceId} UUID as the deterministic tie-breaker (B3), truncated to
   * the top {@code limit} (D3 — no pagination, offset, or cursor). The query is
   * treated as a literal substring (case-insensitive {@code ILIKE} containment);
   * no filter vocabulary, stemming, or ranking is applied.
   *
   * <p>Tenant isolation (data-model.md §2/§5): the adapter only ever matches
   * evidence rows of the given tenant. A synchronous failure (e.g. the evidence
   * store is unavailable) degrades to zero records via the caller's
   * {@code degraded} result (B4) — no retry, per the EVIDENCE_SEARCH failure
   * policy (ports-and-adapters.md §4).
   *
   * @param tenantId the context tenant whose evidence may be searched
   * @param query the free-text query matched against title/content (never blank)
   * @param limit the maximum number of results to return (top-N)
   * @return the matching evidence records, ordered and truncated; empty on no
   *     match (or, raised as a {@code PortException}, on synchronous failure)
   */
  List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit);
}