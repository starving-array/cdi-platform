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

  @Bean
  public com.cdi.application.deployment.RecordDeploymentHandler recordDeploymentHandler(
      com.cdi.application.port.out.DeploymentRepository deploymentRepository,
      com.cdi.application.port.out.ServiceRepository serviceRepository) {
    return new com.cdi.application.deployment.RecordDeploymentHandler(deploymentRepository, serviceRepository);
  }

  @Bean
  public com.cdi.application.deployment.RecordDeploymentOutcomeHandler recordDeploymentOutcomeHandler(
      com.cdi.application.port.out.DeploymentRepository deploymentRepository) {
    return new com.cdi.application.deployment.RecordDeploymentOutcomeHandler(deploymentRepository);
  }

  @Bean
  public com.cdi.application.deployment.GetDeploymentQueryService getDeploymentQueryService(
      com.cdi.application.port.out.DeploymentRepository deploymentRepository) {
    return new com.cdi.application.deployment.GetDeploymentQueryService(deploymentRepository);
  }

  @Bean
  public com.cdi.application.deployment.ListDeploymentsQueryService listDeploymentsQueryService(
      com.cdi.application.port.out.DeploymentRepository deploymentRepository) {
    return new com.cdi.application.deployment.ListDeploymentsQueryService(deploymentRepository);
  }

  @Bean
  public com.cdi.application.attribution.GetDeploymentAttributionQueryService getDeploymentAttributionQueryService(
      com.cdi.application.port.out.DecisionAttributionRepository decisionAttributionRepository,
      com.cdi.application.port.out.DeploymentRepository deploymentRepository,
      com.cdi.application.port.out.AnalysisRunRepository analysisRunRepository,
      com.cdi.application.port.out.RiskAssessmentRepository riskAssessmentRepository,
      com.cdi.application.port.out.DecisionRecordRepository decisionRecordRepository) {
    return new com.cdi.application.attribution.GetDeploymentAttributionQueryService(
        decisionAttributionRepository,
        deploymentRepository,
        analysisRunRepository,
        riskAssessmentRepository,
        decisionRecordRepository);
  }

  @Bean
  public com.cdi.application.attribution.GetAttributionSummaryQueryService getAttributionSummaryQueryService(
      com.cdi.application.port.out.DecisionAttributionRepository decisionAttributionRepository) {
    return new com.cdi.application.attribution.GetAttributionSummaryQueryService(decisionAttributionRepository);
  }
}