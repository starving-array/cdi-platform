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
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.application.port.out.SystemContextPort;
import com.cdi.application.port.out.AgentPort;
import com.cdi.application.port.out.JobQueuePort;
import com.cdi.application.port.out.AgentInvestigationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.application.common.event.DomainEventPublisher;
import com.cdi.analysis.domain.FileDiff;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.ServiceDependency;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.InvestigationFindings;
import com.cdi.application.port.out.AgentContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.time.Clock;

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

  // --- LOCAL BETA ADAPTERS ---

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(SourceControlPort.class)
  public SourceControlPort sourceControlPort() {
    return new SourceControlPort() {
      @Override
      public ChangeMetadata getChangeMetadata(TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
        return new ChangeMetadata(providerChangeId, "dummy title", "dummy desc", "dummy author", "feature-branch", "main", "dummySha");
      }
      @Override
      public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
        return List.of();
      }
      @Override
      public void publishStatusCheck(TenantId tenantId, RepositoryId repositoryId, String commitSha, DecisionOutcome outcome, List<DecisionReason> reasons, String detailsUrl) {
      }
    };
  }

  @Bean
  public SystemContextPort systemContextPort(org.springframework.core.env.Environment env) {
    return new SystemContextPort() {
      @Override
      public CriticalityTier getServiceCriticality(TenantId tenantId, RepositoryId repositoryId, List<String> filePaths) {
        boolean isTest = java.util.Arrays.asList(env.getActiveProfiles()).contains("test"); return isTest ? CriticalityTier.TIER_3 : CriticalityTier.TIER_0;
      }
      @Override
      public List<ServiceDependency> getDependencies(TenantId tenantId, ServiceId serviceId) {
        return List.of();
      }
    };
  }

  @Bean
  public AgentPort agentPort() {
    return new AgentPort() {
      @Override
      public InvestigationFindings investigate(AgentContext context, RiskAssessment riskAssessment, List<EvidenceRecord> evidence) {
        return new InvestigationFindings(List.of());
      }
    };
  }

  @Bean
  public com.cdi.risk.domain.DeterministicRiskEngine deterministicRiskEngine() {
    return new com.cdi.risk.domain.DeterministicRiskEngine();
  }

  @Bean
  public com.cdi.policy.domain.PolicyEngine policyEngine() {
    return new com.cdi.policy.domain.PolicyEngine();
  }

  @Bean
  public com.cdi.application.analysis.AnalyzeChangeHandler analyzeChangeHandler(
      ChangeRepository changeRepository,
      AnalysisRunRepository analysisRunRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      SourceControlPort sourceControlPort,
      SystemContextPort systemContextPort,
      EvidenceSearchPort evidenceSearchPort,
      JobQueuePort jobQueuePort,
      DomainEventPublisher domainEventPublisher,
      com.cdi.risk.domain.DeterministicRiskEngine deterministicRiskEngine) {
    return new com.cdi.application.analysis.AnalyzeChangeHandler(
        changeRepository, analysisRunRepository, riskAssessmentRepository,
        sourceControlPort, systemContextPort, evidenceSearchPort, jobQueuePort,
        domainEventPublisher, deterministicRiskEngine, Clock.systemUTC());
  }

  @Bean
  public com.cdi.application.analysis.InvestigateRiskHandler investigateRiskHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      AgentInvestigationRepository agentInvestigationRepository,
      SourceControlPort sourceControlPort,
      EvidenceSearchPort evidenceSearchPort,
      AgentPort agentPort,
      JobQueuePort jobQueuePort,
      DomainEventPublisher domainEventPublisher) {
    return new com.cdi.application.analysis.InvestigateRiskHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        agentInvestigationRepository, sourceControlPort, evidenceSearchPort, agentPort,
        jobQueuePort, domainEventPublisher, Clock.systemUTC());
  }

  @Bean
  public com.cdi.application.decision.EvaluatePolicyHandler evaluatePolicyHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      RiskAssessmentRepository riskAssessmentRepository,
      PolicyRepository policyRepository,
      DecisionRecordRepository decisionRecordRepository,
      SourceControlPort sourceControlPort,
      SystemContextPort systemContextPort,
      com.cdi.policy.domain.PolicyEngine policyEngine,
      JobQueuePort jobQueuePort,
      DomainEventPublisher domainEventPublisher) {
    return new com.cdi.application.decision.EvaluatePolicyHandler(
        analysisRunRepository, changeRepository, riskAssessmentRepository,
        policyRepository, decisionRecordRepository, sourceControlPort, systemContextPort,
        policyEngine, jobQueuePort, domainEventPublisher, Clock.systemUTC());
  }

  @Bean
  public com.cdi.application.decision.GenerateDecisionHandler generateDecisionHandler(
      AnalysisRunRepository analysisRunRepository,
      ChangeRepository changeRepository,
      DecisionRecordRepository decisionRecordRepository,
      SourceControlPort sourceControlPort) {
    return new com.cdi.application.decision.GenerateDecisionHandler(
        analysisRunRepository, changeRepository, decisionRecordRepository,
        sourceControlPort, Clock.systemUTC());
  }
}
