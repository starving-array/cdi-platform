package com.cdi.common.adapter.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandlerConfiguration {

  @Bean
  public com.cdi.application.change.ProposeChangeHandler proposeChangeHandler(
      com.cdi.application.port.out.ChangeRepository changeRepository,
      com.cdi.application.port.out.AnalysisRunRepository analysisRunRepository,
      com.cdi.application.common.event.DomainEventPublisher eventPublisher,
      com.cdi.application.port.out.JobQueuePort jobQueuePort) {
    return new com.cdi.application.change.ProposeChangeHandler(
        changeRepository, analysisRunRepository, eventPublisher, jobQueuePort);
  }

  @Bean
  public com.cdi.common.adapter.out.queue.InProcessJobQueueDispatcher inProcessJobQueueDispatcher(
      org.springframework.beans.factory.ObjectProvider<com.cdi.application.analysis.AnalyzeChangeHandler> analyzeChangeHandlerProvider,
      org.springframework.beans.factory.ObjectProvider<com.cdi.application.analysis.InvestigateRiskHandler> investigateRiskHandlerProvider,
      org.springframework.beans.factory.ObjectProvider<com.cdi.application.decision.EvaluatePolicyHandler> evaluatePolicyHandlerProvider,
      org.springframework.beans.factory.ObjectProvider<com.cdi.application.decision.GenerateDecisionHandler> generateDecisionHandlerProvider,
      org.springframework.beans.factory.ObjectProvider<com.cdi.application.port.out.AnalysisRunRepository> analysisRunRepositoryProvider) {
    return new com.cdi.common.adapter.out.queue.InProcessJobQueueDispatcher(
        analyzeChangeHandlerProvider,
        investigateRiskHandlerProvider,
        evaluatePolicyHandlerProvider,
        generateDecisionHandlerProvider,
        analysisRunRepositoryProvider,
        java.util.concurrent.Executors.newFixedThreadPool(4));
  }

  @Bean
  public com.cdi.application.repository.CreateRepositoryHandler createRepositoryHandler(
      com.cdi.application.port.out.RepositoryRepository repositoryRepository) {
    return new com.cdi.application.repository.CreateRepositoryHandler(repositoryRepository);
  }

  }
