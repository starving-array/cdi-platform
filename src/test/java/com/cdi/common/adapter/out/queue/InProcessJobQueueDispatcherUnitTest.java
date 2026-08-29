package com.cdi.common.adapter.out.queue;

import com.cdi.application.analysis.AnalyzeChangeHandler;
import com.cdi.application.analysis.InvestigateRiskHandler;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.decision.EvaluatePolicyHandler;
import com.cdi.application.decision.GenerateDecisionHandler;
import com.cdi.application.port.in.AnalyzeChangeCommand;
import com.cdi.application.port.in.EvaluatePolicyCommand;
import com.cdi.application.port.in.GenerateDecisionCommand;
import com.cdi.application.port.in.InvestigateRiskCommand;
import com.cdi.application.port.out.JobId;
import com.cdi.common.domain.id.AnalysisRunId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InProcessJobQueueDispatcherUnitTest {

  private ObjectProvider<AnalyzeChangeHandler> analyzeProvider;
  private ObjectProvider<InvestigateRiskHandler> investigateProvider;
  private ObjectProvider<EvaluatePolicyHandler> evaluateProvider;
  private ObjectProvider<GenerateDecisionHandler> generateProvider;

  private AnalyzeChangeHandler analyzeChangeHandler;
  private InvestigateRiskHandler investigateRiskHandler;
  private EvaluatePolicyHandler evaluatePolicyHandler;
  private GenerateDecisionHandler generateDecisionHandler;

  private ExecutorService executor;
  private InProcessJobQueueDispatcher dispatcher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    analyzeProvider = mock(ObjectProvider.class);
    investigateProvider = mock(ObjectProvider.class);
    evaluateProvider = mock(ObjectProvider.class);
    generateProvider = mock(ObjectProvider.class);

    analyzeChangeHandler = mock(AnalyzeChangeHandler.class);
    investigateRiskHandler = mock(InvestigateRiskHandler.class);
    evaluatePolicyHandler = mock(EvaluatePolicyHandler.class);
    generateDecisionHandler = mock(GenerateDecisionHandler.class);

    when(analyzeProvider.getIfAvailable()).thenReturn(analyzeChangeHandler);
    when(investigateProvider.getIfAvailable()).thenReturn(investigateRiskHandler);
    when(evaluateProvider.getIfAvailable()).thenReturn(evaluatePolicyHandler);
    when(generateProvider.getIfAvailable()).thenReturn(generateDecisionHandler);

    ObjectProvider<com.cdi.application.port.out.AnalysisRunRepository> analysisRunRepositoryProvider = mock(ObjectProvider.class);

    executor = Executors.newFixedThreadPool(2);
    dispatcher = new InProcessJobQueueDispatcher(
        analyzeProvider, investigateProvider, evaluateProvider, generateProvider, analysisRunRepositoryProvider, executor);
  }

  @AfterEach
  void tearDown() {
    dispatcher.shutdown();
  }

  @Test
  void enqueueAnalyzeChangeCommandDispatchesToHandler() {
    AnalysisRunId runId = AnalysisRunId.generate();
    AtomicBoolean called = new AtomicBoolean(false);

    org.mockito.Mockito.doAnswer(inv -> {
      called.set(true);
      return null;
    }).when(analyzeChangeHandler).handle(new AnalyzeChangeCommand(runId));

    JobId jobId = dispatcher.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(runId), new IdempotencyKey("k1"));
    assertNotNull(jobId);

    await().atMost(3, TimeUnit.SECONDS).untilTrue(called);
  }

  @Test
  void enqueueInvestigateRiskCommandDispatchesToHandler() {
    AnalysisRunId runId = AnalysisRunId.generate();
    AtomicBoolean called = new AtomicBoolean(false);

    org.mockito.Mockito.doAnswer(inv -> {
      called.set(true);
      return null;
    }).when(investigateRiskHandler).handle(new InvestigateRiskCommand(runId));

    JobId jobId = dispatcher.enqueue("InvestigateRiskCommand", new InvestigateRiskCommand(runId), new IdempotencyKey("k2"));
    assertNotNull(jobId);

    await().atMost(3, TimeUnit.SECONDS).untilTrue(called);
  }

  @Test
  void enqueueEvaluatePolicyCommandDispatchesToHandler() {
    AnalysisRunId runId = AnalysisRunId.generate();
    AtomicBoolean called = new AtomicBoolean(false);

    org.mockito.Mockito.doAnswer(inv -> {
      called.set(true);
      return null;
    }).when(evaluatePolicyHandler).handle(new EvaluatePolicyCommand(runId));

    JobId jobId = dispatcher.enqueue("EvaluatePolicyCommand", new EvaluatePolicyCommand(runId), new IdempotencyKey("k3"));
    assertNotNull(jobId);

    await().atMost(3, TimeUnit.SECONDS).untilTrue(called);
  }

  @Test
  void enqueueGenerateDecisionCommandDispatchesToHandler() {
    AnalysisRunId runId = AnalysisRunId.generate();
    AtomicBoolean called = new AtomicBoolean(false);

    org.mockito.Mockito.doAnswer(inv -> {
      called.set(true);
      return null;
    }).when(generateDecisionHandler).handle(new GenerateDecisionCommand(runId));

    JobId jobId = dispatcher.enqueue("GenerateDecisionCommand", new GenerateDecisionCommand(runId), new IdempotencyKey("k4"));
    assertNotNull(jobId);

    await().atMost(3, TimeUnit.SECONDS).untilTrue(called);
  }

  @Test
  void multipleQueuedCommandsAllExecuteWithoutLoss() {
    int total = 20;
    AtomicInteger count = new AtomicInteger(0);

    org.mockito.Mockito.doAnswer(inv -> {
      count.incrementAndGet();
      return null;
    }).when(analyzeChangeHandler).handle(org.mockito.ArgumentMatchers.any());

    for (int i = 0; i < total; i++) {
      dispatcher.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(AnalysisRunId.generate()), new IdempotencyKey("k-" + i));
    }

    await().atMost(5, TimeUnit.SECONDS).until(() -> count.get() == total);
    assertEquals(total, count.get());
  }

  @Test
  void handlerExceptionDoesNotCorruptDispatcherOrKillWorker() {
    AnalysisRunId failRunId = AnalysisRunId.generate();
    AnalysisRunId successRunId = AnalysisRunId.generate();
    AtomicBoolean successExecuted = new AtomicBoolean(false);

    org.mockito.Mockito.doThrow(new RuntimeException("Simulated worker error"))
        .when(analyzeChangeHandler).handle(new AnalyzeChangeCommand(failRunId));

    org.mockito.Mockito.doAnswer(inv -> {
      successExecuted.set(true);
      return null;
    }).when(analyzeChangeHandler).handle(new AnalyzeChangeCommand(successRunId));

    dispatcher.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(failRunId), new IdempotencyKey("k-fail"));
    dispatcher.enqueue("AnalyzeChangeCommand", new AnalyzeChangeCommand(successRunId), new IdempotencyKey("k-success"));

    await().atMost(3, TimeUnit.SECONDS).untilTrue(successExecuted);
  }
}