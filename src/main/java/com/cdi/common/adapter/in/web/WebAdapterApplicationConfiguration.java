package com.cdi.common.adapter.in.web;

import com.cdi.application.change.GetChangeQueryService;
import com.cdi.application.change.ListChangesQueryService;
import com.cdi.application.evidence.SearchEvidenceQueryService;
import com.cdi.application.port.out.AnalysisRunRepository;
import com.cdi.application.port.out.ChangeRepository;
import com.cdi.application.port.out.DecisionRecordRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.application.port.out.RiskAssessmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration registering the application query services required
 * by the REST web adapter controllers.
 */
@Configuration
public class WebAdapterApplicationConfiguration {

  @Bean
  public ListChangesQueryService listChangesQueryService(
      ChangeRepository changeRepository,
      RepositoryRepository repositoryRepository) {
    return new ListChangesQueryService(changeRepository, repositoryRepository);
  }

  @Bean
  public GetChangeQueryService getChangeQueryService(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      DecisionRecordRepository decisionRecordRepository) {
    return new GetChangeQueryService(
        changeRepository,
        analysisRunRepository,
        riskAssessmentRepository,
        decisionRecordRepository);
  }

  @Bean
  public SearchEvidenceQueryService searchEvidenceQueryService(
      EvidenceSearchPort evidenceSearchPort) {
    return new SearchEvidenceQueryService(evidenceSearchPort);
  }
}