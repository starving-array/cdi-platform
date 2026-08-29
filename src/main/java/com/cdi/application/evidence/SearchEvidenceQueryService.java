package com.cdi.application.evidence;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.port.in.SearchEvidenceQuery;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;

import java.util.List;
import java.util.Objects;

/**
 * Application query service for the P2 SearchEvidence capability
 * (use-cases.md §6.2, ADR-008). Synchronous, read-only: searches the
 * deterministic evidence store ({@code title}/{@code content}, SQL
 * {@code ILIKE}) for the effective tenant, returning the top-N records ordered
 * {@code capturedAt} ascending with the {@code EvidenceId} UUID as
 * tie-breaker. Never mutates state, never opens a write transaction, and is
 * never idempotency-keyed (queries are not keyed — application-layer.md §6).
 *
 * <p><b>Port exception (ADR-008)</b>: this query is the one sanctioned
 * exception to the "queries never call external ports" rule
 * (application-layer.md §6:297) — {@code use-cases.md} §6.2 names
 * {@code EvidenceSearchPort} for it, it is synchronous and read-only, and its
 * failure contract degrades rather than raising an application error.
 *
 * <p><b>Actor</b> (use-cases.md §6.2): {@code ENGINEER} or {@code TENANT_ADMIN};
 * any other role maps to {@link ApplicationError#UNAUTHORIZED}.
 *
 * <p><b>Failure semantics (D5, B4)</b>: a synchronous evidence-search failure
 * surfaces as {@code PortException} from {@code EvidenceSearchPort} and is
 * converted here into {@code SearchEvidenceResult(degraded = true,
 * List.of())}; {@code ApplicationError} is untouched and
 * {@code EVIDENCE_COLLECTION_FAILED} stays reserved for the async worker path.
 */
public final class SearchEvidenceQueryService {

  private final EvidenceSearchPort evidenceSearchPort;

  public SearchEvidenceQueryService(EvidenceSearchPort evidenceSearchPort) {
    this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "EvidenceSearchPort");
  }

  public SearchEvidenceResult handle(SearchEvidenceQuery query, TenantId tenantId, Actor actor) {
    requireRole(actor);
    try {
      List<EvidenceRecord> records =
          evidenceSearchPort.searchByQuery(tenantId, query.query(), query.limit());
      return new SearchEvidenceResult(false, records);
    } catch (PortException e) {
      return new SearchEvidenceResult(true, List.of());
    }
  }

  private void requireRole(Actor actor) {
    if (actor == null) {
      throw new DomainException("Actor cannot be null");
    }
    if (actor.role() != Actor.Role.ENGINEER && actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}