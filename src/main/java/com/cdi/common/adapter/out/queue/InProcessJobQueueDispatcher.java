package com.cdi.common.adapter.out.queue;

import com.cdi.analysis.domain.AnalysisFailure;
import com.cdi.analysis.domain.AnalysisRun;
import com.cdi.application.analysis.AnalyzeChangeHandler;
import com.cdi.application.analysis.InvestigateRiskHandler;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.decision.EvaluatePolicyHandler;
import com.cdi.application.decision.GenerateDecisionHandler;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.AnalysisRunContext;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.JobId;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.common.domain.id.AnalysisRunId;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight, thread-safe in-process asynchronous JobQueuePort dispatcher for local/beta execution.
 * Dispatches commands enqueued across the UC-01 -> UC-06 pipeline to their respective application handlers.
 */
public class InProcessJobQueueDispatcher implements JobQueuePort {
    private void markRunFailed(AnalysisRunId runId, String error) {
      com.cdi.application.port.out.AnalysisRunRepository repo = analysisRunRepositoryProvider.getIfAvailable();
      if (repo != null) {
        repo.findById(runId).ifPresent(context -> {
          com.cdi.analysis.domain.AnalysisRun run = context.run();
          if (run.getStatus() == com.cdi.analysis.domain.AnalysisRun.Status.QUEUED || run.getStatus() == com.cdi.analysis.domain.AnalysisRun.Status.RUNNING) {
            run.fail(new com.cdi.analysis.domain.AnalysisFailure(com.cdi.analysis.domain.AnalysisFailure.FailureCategory.ANALYSIS_FAILED, "Dispatcher handler missing", java.time.Instant.now()));
            repo.save(context.tenantId(), run);
          }
        });
      }
    }

  private static final Logger log = LoggerFactory.getLogger(InProcessJobQueueDispatcher.class);

  private final ObjectProvider<AnalyzeChangeHandler> analyzeChangeHandlerProvider;
  private final ObjectProvider<InvestigateRiskHandler> investigateRiskHandlerProvider;
  private final ObjectProvider<EvaluatePolicyHandler> evaluatePolicyHandlerProvider;
  private final ObjectProvider<GenerateDecisionHandler> generateDecisionHandlerProvider;
  private final ObjectProvider<AnalysisRunRepository> analysisRunRepositoryProvider;
  private final ExecutorService executor;

  public InProcessJobQueueDispatcher(
      ObjectProvider<AnalyzeChangeHandler> analyzeChangeHandlerProvider,
      ObjectProvider<InvestigateRiskHandler> investigateRiskHandlerProvider,
      ObjectProvider<EvaluatePolicyHandler> evaluatePolicyHandlerProvider,
      ObjectProvider<GenerateDecisionHandler> generateDecisionHandlerProvider,
      ObjectProvider<AnalysisRunRepository> analysisRunRepositoryProvider,
      ExecutorService executor) {
    this.analyzeChangeHandlerProvider = analyzeChangeHandlerProvider;
    this.investigateRiskHandlerProvider = investigateRiskHandlerProvider;
    this.evaluatePolicyHandlerProvider = evaluatePolicyHandlerProvider;
    this.generateDecisionHandlerProvider = generateDecisionHandlerProvider;
    this.analysisRunRepositoryProvider = analysisRunRepositoryProvider;
    this.executor = executor != null
        ? executor
        : Executors.newFixedThreadPool(4, r -> {
          Thread t = new Thread(r, "cdi-job-dispatcher");
          t.setDaemon(true);
          return t;
        });
  }

  @Override
  public JobId enqueue(String commandName, Object payload, IdempotencyKey key) {
    String jobIdValue = UUID.randomUUID().toString();
    JobId jobId = new JobId(jobIdValue);

    executor.submit(() -> {
      int maxRetries = 3;
      long backoffMs = 1000;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          dispatch(commandName, payload);
          return; // Success
        } catch (Throwable t) {
          boolean retryable = isRetryable(t);
          if (!retryable || attempt == maxRetries) {
            handleTerminalFailure(commandName, payload, t);
            return;
          }
          log.warn("Job '{}' failed (attempt {}/{}), retrying in {}ms: {}", commandName, attempt, maxRetries, backoffMs, t.getMessage());
          try {
            Thread.sleep(backoffMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleTerminalFailure(commandName, payload, e);
            return;
          }
          backoffMs *= 2;
        }
      }
    });

    return jobId;
  }
  
  private boolean isRetryable(Throwable t) {
    if (t instanceof ApplicationException ae) {
      return ae.getError().retryable();
    }
    // General runtime exceptions or port exceptions could be transient.
    return true;
  }
  
  private void handleTerminalFailure(String commandName, Object payload, Throwable t) {
    AnalysisRunId runId = extractRunId(payload);
    String analysisRunIdStr = runId != null ? runId.value().toString() : "unknown";
    
    log.error("Unhandled error processing job '{}' with payload [{}], analysisRunId={}. Exception Type: {}. Full Stack Trace:", 
        commandName, payload, analysisRunIdStr, t.getClass().getName(), t);
        
    if (runId != null && analysisRunRepositoryProvider != null) {
      AnalysisRunRepository repo = analysisRunRepositoryProvider.getIfAvailable();
      if (repo != null) {
        try {
          Optional<AnalysisRunContext> ctx = repo.findById(runId);
          if (ctx.isPresent()) {
            AnalysisRun run = ctx.get().run();
            // Mark the run as failed so it can be safely retried via RequestAnalysis
            run.fail(new AnalysisFailure(
                AnalysisFailure.FailureCategory.ANALYSIS_FAILED,
                t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName(),
                Instant.now()
            ));
            repo.save(ctx.get().tenantId(), run);
            log.info("Marked analysis_run {} as FAILED", analysisRunIdStr);
          }
        } catch (Exception e) {
          log.error("Failed to mark analysis_run {} as FAILED", analysisRunIdStr, e);
        }
      }
    }
  }
  
  private AnalysisRunId extractRunId(Object payload) {
    if (payload instanceof EvaluatePolicyCommand cmd) return cmd.analysisRunId();
    if (payload instanceof InvestigateRiskCommand cmd) return cmd.analysisRunId();
    if (payload instanceof GenerateDecisionCommand cmd) return cmd.analysisRunId();
    if (payload instanceof AnalyzeChangeCommand cmd) return cmd.analysisRunId();
    return null;
  }

  @Override
  public void cancel(IdempotencyKey key) {
    log.debug("Job cancellation requested for key: {}", key);
  }

  private void dispatch(String commandName, Object payload) {
    if (payload instanceof AnalyzeChangeCommand cmd) {
      AnalyzeChangeHandler handler = analyzeChangeHandlerProvider.getIfAvailable();
      if (handler != null) {
        handler.handle(cmd);
      } else {
        String msg = "AnalyzeChangeHandler bean not available to process AnalyzeChangeCommand for run: " + cmd.analysisRunId(); log.error(msg); markRunFailed(cmd.analysisRunId(), msg); throw new IllegalStateException(msg);
      }
    } else if (payload instanceof InvestigateRiskCommand cmd) {
      InvestigateRiskHandler handler = investigateRiskHandlerProvider.getIfAvailable();
      if (handler != null) {
        handler.handle(cmd);
      } else {
        String msg = "InvestigateRiskHandler bean not available to process InvestigateRiskCommand for run: " + cmd.analysisRunId(); log.error(msg); markRunFailed(cmd.analysisRunId(), msg); throw new IllegalStateException(msg);
      }
    } else if (payload instanceof EvaluatePolicyCommand cmd) {
      EvaluatePolicyHandler handler = evaluatePolicyHandlerProvider.getIfAvailable();
      if (handler != null) {
        handler.handle(cmd);
      } else {
        String msg = "EvaluatePolicyHandler bean not available to process EvaluatePolicyCommand for run: " + cmd.analysisRunId(); log.error(msg); markRunFailed(cmd.analysisRunId(), msg); throw new IllegalStateException(msg);
      }
    } else if (payload instanceof GenerateDecisionCommand cmd) {
      GenerateDecisionHandler handler = generateDecisionHandlerProvider.getIfAvailable();
      if (handler != null) {
        handler.handle(cmd);
      } else {
        String msg = "GenerateDecisionHandler bean not available to process GenerateDecisionCommand for run: " + cmd.analysisRunId(); log.error(msg); markRunFailed(cmd.analysisRunId(), msg); throw new IllegalStateException(msg);
      }
    } else {
      log.warn("Unknown job commandName '{}' with payload type: {}", commandName, payload != null ? payload.getClass().getName() : "null");
    }
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}