package com.cdi.investigation.adapter.out.persistence;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.InvestigationId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.investigation.domain.AgentInvestigation;
import com.cdi.investigation.domain.AgentInvestigation.Status;
import com.cdi.investigation.domain.EvidenceCitation;
import com.cdi.investigation.domain.InvestigationFailure;
import com.cdi.investigation.domain.InvestigationFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps between the {@code AgentInvestigation} aggregate and its persistence
 * representation (parent + findings).
 *
 * <p>Horizontal conversion only: the aggregate's tenant scope is supplied from
 * the port call because {@code TenantId} does not live on the aggregate
 * (data-model.md §2); immutable {@code InvestigationFinding} children flatten
 * to the {@code investigation_finding} table with evidence citations joined as
 * a comma-separated UUID list (children are never queried by that column,
 * data-model.md §7).
 */
final class AgentInvestigationMapper {

  private AgentInvestigationMapper() {
    // utility class
  }

  static AgentInvestigationEntity toEntity(TenantId tenantId, AgentInvestigation investigation) {
    AgentInvestigationEntity entity = new AgentInvestigationEntity();
    entity.setId(investigation.getId().value());
    entity.setTenantId(tenantId.value());
    entity.setAnalysisRunId(investigation.getAnalysisRunId().value());
    entity.setChangeId(investigation.getChangeId().value());
    entity.setRiskAssessmentId(investigation.getRiskAssessmentId().value());
    entity.setStatus(investigation.getStatus().name());
    investigation.getFailure().ifPresent(failure -> {
      entity.setFailureCategory(failure.category().name());
      entity.setFailureCode(failure.failureCode());
    });
    entity.setCreatedAt(investigation.getCreatedAt());
    investigation.getCompletedAt().ifPresent(entity::setCompletedAt);

    List<InvestigationFindingEntity> findingEntities = new ArrayList<>();
    for (InvestigationFinding finding : investigation.getFindings()) {
      InvestigationFindingEntity findingEntity = new InvestigationFindingEntity();
      findingEntity.setId(UUID.randomUUID());
      findingEntity.setTenantId(tenantId.value());
      findingEntity.setAgentInvestigation(entity);
      findingEntity.setSummary(finding.summary());
      findingEntity.setExplanation(finding.explanation());
      findingEntity.setEvidenceCitationIds(join(finding.citedEvidenceIds()));
      findingEntities.add(findingEntity);
    }
    entity.setFindings(findingEntities);
    return entity;
  }

  static AgentInvestigation toDomain(AgentInvestigationEntity entity) {
    List<InvestigationFinding> findings = new ArrayList<>();
    for (InvestigationFindingEntity findingEntity : entity.getFindings()) {
      findings.add(new InvestigationFinding(
          findingEntity.getSummary(),
          findingEntity.getExplanation(),
          split(findingEntity.getEvidenceCitationIds()).stream()
              .map(EvidenceCitation::of)
              .collect(Collectors.toList())));
    }

    InvestigationFailure failure = null;
    if (entity.getFailureCategory() != null) {
      failure = new InvestigationFailure(
          InvestigationFailure.FailureCategory.valueOf(entity.getFailureCategory()),
          entity.getFailureCode(), entity.getCompletedAt());
    }

    return AgentInvestigation.restore(
        new InvestigationId(entity.getId()),
        new AnalysisRunId(entity.getAnalysisRunId()),
        new ChangeId(entity.getChangeId()),
        new RiskAssessmentId(entity.getRiskAssessmentId()),
        Status.valueOf(entity.getStatus()),
        findings,
        failure,
        entity.getCreatedAt(),
        entity.getCompletedAt());
  }

  private static String join(List<EvidenceId> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return ids.stream().map(id -> id.value().toString()).collect(Collectors.joining(","));
  }

  private static List<EvidenceId> split(String joined) {
    if (joined == null || joined.isBlank()) {
      return List.of();
    }
    List<EvidenceId> ids = new ArrayList<>();
    for (String part : joined.split(",")) {
      if (!part.isBlank()) {
        ids.add(new EvidenceId(UUID.fromString(part.trim())));
      }
    }
    return ids;
  }
}
