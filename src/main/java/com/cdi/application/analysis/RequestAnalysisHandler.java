package com.cdi.application.analysis;

import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.analysis.domain.CodeSnapshot;
import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.error.PortException;
import com.cdi.application.common.result.IdempotentCommandResult;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.RequestAnalysisCommand;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.change.domain.Change;
import com.cdi.common.domain.id.AnalysisRunId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Application use case UC-02 RequestAnalysis (application-layer.md §6, §10;
 * use-cases.md §5.3). Manual or internal re-analysis of a change snapshot,
 * owned by the Engineer actor.
 *
 * <p>Idempotency is owned here per §10: the run is always resolved by its
 * uniqueness triple {@code (tenantId, changeId, commitSha)} (data-model.md §5)
 * and never duplicated. The existing run's state decides the behavior:
 * QUEUED/RUNNING/COMPLETED and SUPERSEDED are returned idempotently
 * ({@code created=false}) with no enqueue; FAILED is retried via the domain
 * {@link AnalysisRun#retry()} (FAILED→QUEUED) and re-enqueued exactly once,
 * collapsing concurrent retries to one {@code analysis_job}. No run exists
 * only when the requested commit is the change's current snapshot: exactly one
 * new QUEUED run is created, persisted and enqueued once; a non-current commit
 * that has no historical run is rejected with {@link ApplicationError#ANALYSIS_SUPERSEDED}.
 *
 * <p>Role guard follows §9.1 (UC-02 actor is Engineer only). Domain-invariant
 * failures are translated to the {@link ApplicationException} boundary; the API
 * layer never sees raw domain/persistence/queue exceptions, which instead
 * propagate via their own port exceptions per §9.2.
 */
public final class RequestAnalysisHandler {

  private final ChangeRepository changeRepository;
  private final AnalysisRunRepository analysisRunRepository;
  private final JobQueuePort jobQueuePort;
  private final Clock clock;

  /**
   * Creates the handler with explicit dependencies and a time source.
   */
  public RequestAnalysisHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      JobQueuePort jobQueuePort,
      Clock clock) {
    this.changeRepository = changeRepository;
    this.analysisRunRepository = analysisRunRepository;
    this.jobQueuePort = jobQueuePort;
    this.clock = clock;
  }

  /**
   * Creates the handler using the system clock.
   */
  public RequestAnalysisHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      JobQueuePort jobQueuePort) {
    this(changeRepository, analysisRunRepository, jobQueuePort, Clock.systemUTC());
  }

  /**
   * Requests analysis for a change snapshot, returning the resolved run and
   * whether it was newly created.
   *
   * @return the {@link IdempotentCommandResult} carrying the resolved run id
   *     and the created/reused flag
   * @throws ApplicationException for role violations or domain-invariant
   *     failures; persistence/queue failures propagate via their own port
   *     exceptions (webhook 500 per §9.2)
   */
  public IdempotentCommandResult handle(RequestAnalysisCommand command) {
    requireRole(command.actor());

    Change change = changeRepository.findByTenantAndId(command.tenantId(), command.changeId())
        .orElseThrow(() -> new ApplicationException(
            ApplicationError.CHANGE_NOT_FOUND,
            Map.of("changeId", command.changeId().value().toString())));

    if (change.getStatus() != Change.Status.OPEN) {
      throw new ApplicationException(
          ApplicationError.CHANGE_NOT_FOUND,
          Map.of("changeId", change.getId().value().toString(),
              "status", change.getStatus().name()));
    }

    Optional<AnalysisRun> existing = analysisRunRepository.findByTenantAndChangeAndCommit(
        command.tenantId(), change.getId(), command.commitSha());

    if (existing.isPresent()) {
      return resolveExisting(existing.get(), command);
    }
    return createForCurrentSnapshot(change, command);
  }

  private IdempotentCommandResult resolveExisting(AnalysisRun run, RequestAnalysisCommand command) {
    switch (run.getStatus()) {
      case QUEUED:
      case RUNNING:
      case COMPLETED:
      case SUPERSEDED:
        return new IdempotentCommandResult(run.getId(), false);
      case FAILED:
        run.retry();
        analysisRunRepository.save(command.tenantId(), run);
        enqueue(run.getId(), command);
        return new IdempotentCommandResult(run.getId(), false);
      default:
        throw new IllegalStateException("Unexpected analysis run status: " + run.getStatus());
    }
  }

  private IdempotentCommandResult createForCurrentSnapshot(
      Change change, RequestAnalysisCommand command) {
    if (!change.getLatestCommitSha().equals(command.commitSha())) {
      throw new ApplicationException(
          ApplicationError.ANALYSIS_SUPERSEDED,
          Map.of("changeId", change.getId().value().toString(),
              "commitSha", command.commitSha(),
              "latestCommitSha", change.getLatestCommitSha()));
    }

    Instant now = clock.instant();
    AnalysisRun run = new AnalysisRun(
        AnalysisRunId.generate(), change.getId(),
        new CodeSnapshot(command.commitSha(), change.getSourceBranch()), now);

    try {
      analysisRunRepository.save(command.tenantId(), run);
    } catch (PortException e) {
      return recoverFromCreateRace(change, command, e);
    }
    enqueue(run.getId(), command);

    return new IdempotentCommandResult(run.getId(), true);
  }

  /**
   * §7 concurrency: two simultaneous requests for the same
   * {@code (tenant, change, commit)} race on the insert; the DB
   * {@code UNIQUE (tenant_id, change_id, commit_sha)} constraint (the safety
   * net) lets one win and the loser surface a persistence port failure here.
   * Re-read by the authoritative triple — when a run now exists, return it
   * with {@code created=false} and <em>no</em> enqueue (the winner queued);
   * otherwise the failure is genuine and rethrown per the port policy.
   */
  private IdempotentCommandResult recoverFromCreateRace(
      Change change, RequestAnalysisCommand command, PortException original) {
    Optional<AnalysisRun> winner = analysisRunRepository.findByTenantAndChangeAndCommit(
        command.tenantId(), change.getId(), command.commitSha());
    if (winner.isPresent()) {
      return new IdempotentCommandResult(winner.get().getId(), false);
    }
    throw original;
  }

  private void enqueue(AnalysisRunId runId, RequestAnalysisCommand command) {
    IdempotencyKey key = new IdempotencyKey(
        command.tenantId().value() + ":" + command.changeId().value()
            + ":" + command.commitSha());
    jobQueuePort.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(runId), key);
  }

  private void requireRole(Actor actor) {
    if (actor.role() != Actor.Role.ENGINEER) {
      throw new ApplicationException(ApplicationError.UNAUTHORIZED);
    }
  }
}