package com.cdi.application.port.in;

import com.cdi.common.domain.exception.DomainException;

import java.util.Map;

/**
 * Input contract for the P2 SearchEvidence query (use-cases.md §6.2, ADR-008).
 * Searches the deterministic evidence store by free-text {@code query} matched
 * against {@code title}/{@code content}, returning the top {@code limit}
 * results ordered {@code capturedAt} asc with the {@code EvidenceId} UUID as
 * tie-breaker (ADR-008 B3, D2, D3).
 *
 * <p><b>Filters (D1)</b>: structurally reserved but empty. No filter names,
 * values, or semantics are invented in this contract or the V10 store — the
 * {@code filters} slot exists so a future, approved vocabulary can be added
 * without a breaking signature change, and the constructor rejects any
 * non-empty use of it today.
 *
 * <p><b>Limit (D2)</b>: defaults to {@link #DEFAULT_LIMIT} ({@code 20}, the
 * EVIDENCE_LIMIT convention) and is caller-overridable; values below 1 are
 * rejected. Result is top-N only (D3 — no pagination, offset, or cursor).
 */
public record SearchEvidenceQuery(String query, Map<String, String> filters, int limit) {

  /** Default result limit (D2): the EVIDENCE_LIMIT code convention. */
  public static final int DEFAULT_LIMIT = 20;

  public SearchEvidenceQuery {
    if (query == null || query.trim().isEmpty()) {
      throw new DomainException("Query cannot be null or blank");
    }
    if (filters == null) {
      throw new DomainException("Filters cannot be null");
    }
    if (!filters.isEmpty()) {
      throw new DomainException("Filters are reserved and must be empty");
    }
    if (limit < 1) {
      throw new DomainException("Limit must be at least 1");
    }
    query = query.trim();
  }

  /**
   * Convenience constructor applying the default {@link #DEFAULT_LIMIT}.
   *
   * @param query the non-blank free-text search query
   * @param filters the filters slot (must be empty — D1)
   */
  public SearchEvidenceQuery(String query, Map<String, String> filters) {
    this(query, filters, DEFAULT_LIMIT);
  }
}