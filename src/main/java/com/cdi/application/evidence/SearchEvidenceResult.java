package com.cdi.application.evidence;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.evidence.domain.EvidenceRecord;

import java.util.List;

/**
 * Read result for the P2 SearchEvidence query (use-cases.md §6.2, ADR-008 D5).
 * Carries the matched records plus a {@code degraded} flag so a synchronous
 * evidence-search failure is expressible as a result value (zero/partial
 * records with {@code degraded = true}) instead of a raised application error.
 *
 * <p>Failure semantics (D5, B4): a synchronous evidence-search failure returns
 * zero records with {@code degraded = true} and renders at the API layer as
 * {@code EVIDENCE_UNAVAILABLE} (api-contract.md §4); {@code EVIDENCE_COLLECTION_FAILED}
 * remains reserved for the existing async/terminal worker path
 * (application-layer.md §9.1) and {@code ApplicationError} is not modified.
 * A successful search returns {@code degraded = false} with the ordered,
 * top-N {@code records}. Immutable; never mutated by the query service.
 */
public record SearchEvidenceResult(boolean degraded, List<EvidenceRecord> records) {

  public SearchEvidenceResult {
    if (records == null) {
      throw new DomainException("Records cannot be null");
    }
  }
}