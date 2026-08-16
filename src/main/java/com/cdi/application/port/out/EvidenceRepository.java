package com.cdi.application.port.out;

import com.cdi.evidence.domain.EvidenceRecord;

/**
 * Outbound persistence port for the {@code EvidenceRecord} aggregate (V10,
 * ADR-008). Persists (inserts) evidence records into the deterministic
 * {@code evidence_record} store.
 *
 * <p>This port exists to make the evidence store seedable and ready for the
 * future ingestion use case — the P2 SearchEvidence delivery itself is
 * read-only and searches only through {@link EvidenceSearchPort#searchByQuery};
 * evidence ingestion is explicitly out of scope for that delivery (ADR-008).
 * The {@code EvidenceRecord} carries its own {@code TenantId}, so no separate
 * {@code tenantId} argument is required.
 *
 * <p>No JPA, Spring Data, SQL, or PostgreSQL types appear on this contract.
 */
public interface EvidenceRepository {

  /**
   * Persists a new {@code EvidenceRecord} (V10). The record carries its own
   * {@code TenantId} (it is a tenant-scoped child aggregate), so the tenant
   * scope is read from the aggregate itself (mirrors {@code PolicyRepository}).
   *
   * @param record the evidence record to persist
   * @return the persisted record, rebuilt via the evidence builder
   */
  EvidenceRecord save(EvidenceRecord record);
}