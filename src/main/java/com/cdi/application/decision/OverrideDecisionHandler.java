package com.cdi.application.decision;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.OverrideDecisionResult;
import com.cdi.application.port.in.OverrideDecisionCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.common.domain.event.DecisionOverridden;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.HumanOverride;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application use case UC-06 OverrideDecision (application-layer.md §6 UC-06,
 * ADR-006, use-cases.md §5.4) — the synchronous, TENANT_ADMIN-only override of
 * a persisted decision outcome.
 *
 * <p><b>Frozen contract (D1/D2/D4)</b>: the override never mutates the original
 * {@code DecisionRecord} — it applies a supplemental, immutable
 * {@link HumanOverride} via {@link DecisionRecord#withOverride(HumanOverride)}
 * and persists the aggregate, so the DB records the override as an additional
 * {@code human_override} row while {@code decision_record} stays exactly as
 * generated (data-model.md §E, V11). A run whose decision is already
 * overridden resolves to {@code DECISION_ALREADY_OVERRIDDEN}; the override is
 * a one-shot, non-idempotent action, never a chain or revision.
 *
 * <p><b>Preconditions (§4, D4)</b>: only a run owned by the command's tenant is
 * eligible ({@code ANALYSIS_RUN_NOT_FOUND} for a missing or cross-tenant run);
 * a superseded run cannot be overridden ({@code ANALYSIS_SUPERSEDED}); the
 * decision must exist for the tenant ({@code DECISION_NOT_FOUND}) and must
 * match the supplied decision id. Missing data is surfaced as a typed
 * {@link ApplicationException}, never a generic {@code RuntimeException}.
 *
 * <p><b>Role &amp; event (§9.1, D3)</b>: the actor must be {@code TENANT_ADMIN}
 * ({@code UNAUTHORIZED} otherwise). On success a {@code DecisionOverridden}
 * domain event is published through {@link DomainEventPublisher} in the same
 * transaction as the saved override (application-layer.md §8/§12). No external
 * ports are called; nothing is enqueued.
 */
public final class OverrideDecisionHandler {

  private final AnalysisRunRepository analysisRunRepository;
  private final DecisionRecordRepository decisionRecordRepository;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public OverrideDecisionHandler(
      AnalysisRunRepository analysisRunRepository,
      DecisionRecordRepository decisionRecordRepository,
      DomainEventPublisher eventPublisher,
      Clock clock) {
    this.analysisRunRepository =
        Objects.requireNonNull(analysisRunRepository, "AnalysisRunRepository");
    this.decisionRecordRepository =
        Objects.requireNonNull(decisionRecordRepository, "DecisionRecordRepository");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "DomainEventPublisher");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Creates the handler using the system clock.
   */
  public OverrideDecisionHandler(
      AnalysisRunRepository analysisRunRepository,
      DecisionRecordRepository decisionRecordRepository,
      DomainEventPublisher eventPublisher) {
    this(analysisRunRepository, decisionRecordRepository, eventPublisher, Clock.systemUTC());
  }

  /**
   * Overrides the decision of an analysis run.
   *
   * @return the {@link OverrideDecisionResult} carrying the decision id, the
   *     immutable original outcome, and the new effective outcome
   * @throws ApplicationException for role violations and the typed
   *     preconditions in D4
   */
  public OverrideDecisionResult handle(OverrideDecisionCommand command) {
    requireRole(command.actor());

    AnalysisRunContext context = resolveRun(command.tenantId(), command.analysisRunId());
    AnalysisRun run = context.run();
    TenantId tenantId = context.tenantId();

    DecisionRecord decision =
        resolveDecision(tenantId, run, command.decisionId());

    Instant overrideAt = clock.instant();
    HumanOverride override = new HumanOverride(
        command.actor().id(),
        decision.getOriginalOutcome(),
        command.newOutcome(),
        command.justification(),
        overrideAt);

    DecisionRecord overridden = decision.withOverride(override);
    decisionRecordRepository.save(tenantId, overridden);

    publish(tenantId, run, decision, overridden, overrideAt);

    return new OverrideDecisionResult(
        overridden.getId(), decision.getOriginalOutcome(), overridden.getOutcome());
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.TENANT_ADMIN) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  /**
   * Resolves the tenant-scoped run. A missing run — or a run owned by a
   * different tenant than the command carries — is {@code ANALYSIS_RUN_NOT_FOUND}.
   */
  private AnalysisRunContext resolveRun(TenantId tenantId, AnalysisRunId analysisRunId) {
    Optional<AnalysisRunContext> context = analysisRunRepository.findById(analysisRunId);
    if (context.isEmpty()) {
      throw new ApplicationException(ApplicationError.ANALYSIS_RUN_NOT_FOUND);
    }
    AnalysisRunContext runContext = context.get();
    if (!runContext.tenantId().equals(tenantId)) {
      throw new ApplicationException(ApplicationError.ANALYSIS_RUN_NOT_FOUND,
          Map.of("reason", "cross-tenant-run"));
    }
    if (runContext.run().getStatus() == AnalysisRun.Status.SUPERSEDED) {
      throw new ApplicationException(ApplicationError.ANALYSIS_SUPERSEDED);
    }
    return runContext;
  }

  /**
   * Resolves the tenant-scoped persisted decision for the run. A missing
   * decision — or one whose id differs from the command's — is
   * {@code DECISION_NOT_FOUND}; an already-overridden decision is
   * {@code DECISION_ALREADY_OVERRIDDEN} (one-shot, non-idempotent).
   */
  private DecisionRecord resolveDecision(
      TenantId tenantId, AnalysisRun run, DecisionId decisionId) {
    DecisionRecord decision = decisionRecordRepository
        .findByAnalysisRunId(tenantId, run.getId())
        .orElseThrow(() -> new ApplicationException(ApplicationError.DECISION_NOT_FOUND));
    if (!decision.getId().equals(decisionId)) {
      throw new ApplicationException(ApplicationError.DECISION_NOT_FOUND,
          Map.of("reason", "decision-id-mismatch"));
    }
    if (decision.getOverride().isPresent()) {
      throw new ApplicationException(ApplicationError.DECISION_ALREADY_OVERRIDDEN);
    }
    return decision;
  }

  private void publish(
      TenantId tenantId, AnalysisRun run, DecisionRecord original,
      DecisionRecord overridden, Instant overrideAt) {
    eventPublisher.publish(new DecisionOverridden(
        UUID.randomUUID(),
        overrideAt,
        tenantId,
        run.getId(),
        run.getChangeId(),
        overridden.getId(),
        original.getOriginalOutcome(),
        overridden.getOutcome(),
        run.getCodeSnapshot().commitSha()));
  }
}