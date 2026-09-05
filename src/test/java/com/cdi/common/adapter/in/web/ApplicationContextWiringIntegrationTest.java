package com.cdi.common.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.cdi.application.analysis.AnalyzeChangeHandler;
import com.cdi.application.analysis.InvestigateRiskHandler;
import com.cdi.application.change.ProposeChangeHandler;
import com.cdi.application.decision.EvaluatePolicyHandler;
import com.cdi.application.decision.GenerateDecisionHandler;
import com.cdi.common.adapter.out.queue.InProcessJobQueueDispatcher;

@SpringBootTest
@org.springframework.context.annotation.Import(com.cdi.testconfig.PostgresTestContainerConfiguration.class)
class ApplicationContextWiringIntegrationTest {

  @Autowired
  private ApplicationContext context;

  @Test
  void realApplicationContextCanResolveAllHandlers() {
    assertNotNull(context.getBean(AnalyzeChangeHandler.class));
    assertNotNull(context.getBean(InvestigateRiskHandler.class));
    assertNotNull(context.getBean(EvaluatePolicyHandler.class));
    assertNotNull(context.getBean(GenerateDecisionHandler.class));
    assertNotNull(context.getBean(ProposeChangeHandler.class));
    assertNotNull(context.getBean(InProcessJobQueueDispatcher.class));
  }
}