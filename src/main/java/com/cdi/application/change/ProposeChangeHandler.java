package com.cdi.application.change;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.ProposeChangeCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.event.ChangeProposed;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Application use case UC-01 ProposeChange (application-layer.md §6, §10;
 * use-cases.md §5.1). Accepts a proposed engineering change into the system
 * and queues it for analysis.
 *
 * <p>Coordinates the existing {@code Change}/{@code AnalysisRun} domain
 * aggregates and the outbound ports only; it never re-implements domain
 * rules. Idempotency is owned here per §10: a request whose {@code
 * (tenantId, repositoryId, providerChangeId)} natural key already exists at
 * the same {@code commitSha} resolves to the existing run with {@code
 * created=false} instead of inserting a duplicate.
 *
 * <p>Role guard follows §9.1: UC-01's actors are ENGINEER and SYSTEM_WORKER.
 * Domain-invariant failures ({@code DomainException}) are translated to the
 * {@link ApplicationException} boundary so the API layer never sees raw
 * domain/persistence exceptions.
 */
public final class ProposeChangeHandler {

  private final ChangeRepository changeRepository;
  private final AnalysisRunRepository analysisRunRepository;
  private final DomainEventPublisher eventPublisher;
  private final JobQueuePort jobQueuePort;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public ProposeChangeHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      DomainEventPublisher eventPublisher,
      JobQueuePort jobQueuePort,
      Clock clock) {
    this.changeRepository = changeRepository;
    this.analysisRunRepository = analysisRunRepository;
    this.eventPublisher = eventPublisher;
    this.jobQueuePort = jobQueuePort;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public ProposeChangeHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      DomainEventPublisher eventPublisher,
      JobQueuePort jobQueuePort) {
    this(changeRepository, analysisRunRepository, eventPublisher, jobQueuePort,
        Clock.systemUTC());
  }

  /**
   * Proposes a change for analysis, returning the queued run and whether it
   * was newly created.
   *
   * @return the {@link IdempotentCommandResult} carrying the tracked run id
   *     and the created/reused flag
   * @throws ApplicationException for role violations or domain-invariant
   *     failures; persistence/queue failures propagate via their own port
   *     exceptions (webhook 500 per §9.2)
   */
  public IdempotentCommandResult handle(ProposeChangeCommand command) {
    requireRole(command.actor());

    Optional<Change> existing = changeRepository.findByTenantAndRepositoryAndProvider(
        command.tenantId(), command.repositoryId(), command.providerChangeId());

    if (existing.isPresent() && existing.get().getLatestCommitSha().equals(command.commitSha())) {
      Optional<AnalysisRun> run = analysisRunRepository.findByTenantAndChangeAndCommit(
          command.tenantId(), existing.get().getId(), command.commitSha());
      if (run.isPresent()) {
        return new IdempotentCommandResult(run.get().getId(), false);
      }
    }

    Instant now = clock.instant();
    Change change;
    if (existing.isEmpty()) {
      change = createChange(command, now);
    } else {
      change = existing.get();
      if (!change.getLatestCommitSha().equals(command.commitSha())) {
        updateLatestCommit(change, command.commitSha(), now);
      }
    }

    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(command.commitSha(), command.branch()), now);

    changeRepository.save(command.tenantId(), change);
    analysisRunRepository.save(command.tenantId(), run);
    eventPublisher.publish(ChangeProposed.create(
        command.tenantId(), command.repositoryId(), command.providerChangeId(),
        change.getId(), command.commitSha(), run.getId()));
    IdempotencyKey key = computeKey(
        command.tenantId(), command.repositoryId(),
        command.providerChangeId(), command.commitSha());
    jobQueuePort.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(run.getId()), key);

    return new IdempotentCommandResult(run.getId(), true);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER
        && actor.role() != Actor.Role.SYSTEM_WORKER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }

  private Change createChange(ProposeChangeCommand command, Instant now) {
    return new Change(
        ChangeId.generate(),
        command.repositoryId(),
        command.providerChangeId(),
        command.title(),
        command.description(),
        command.author(),
        command.branch(),
        "",
        command.commitSha(),
        now);
  }

  private void updateLatestCommit(Change change, String commitSha, Instant now) {
    try {
      change.updateLatestCommit(commitSha, now);
    } catch (DomainException e) {
      throw new ApplicationException(
          ApplicationError.CHANGE_NOT_FOUND,
          e.getMessage(),
          Map.of("changeId", change.getId().value().toString(), "reason", e.getMessage()));
    }
  }

  private IdempotencyKey computeKey(
      TenantId tenantId, RepositoryId repositoryId,
      String providerChangeId, String commitSha) {
    return new IdempotencyKey(
        tenantId.value() + ":" + repositoryId.value()
            + ":" + providerChangeId + ":" + commitSha);
  }
}