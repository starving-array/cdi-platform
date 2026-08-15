package com.cdi.risk.adapter.out.persistence;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskAssessment;
import com.cdi.risk.domain.RiskFactor;
import com.cdi.risk.domain.RiskFactorType;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.risk.domain.RiskScore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps between the {@code RiskAssessment} aggregate and its persistence
 * representation (parent + factors).
 *
 * <p>Horizontal conversion only: the aggregate's tenant scope is supplied from
 * the port call because {@code TenantId} does not live on the aggregate
 * (data-model.md §2); immutable {@code RiskFactor} children flatten to the
 * {@code risk_factor} table with evidence references joined as a comma
 * separated UUID list (children are never queried by that column,
 * data-model.md §7).
 */
final class RiskAssessmentMapper {

  private RiskAssessmentMapper() {
    // utility class
  }

  static RiskAssessmentEntity toEntity(TenantId tenantId, RiskAssessment assessment) {
    RiskAssessmentEntity entity = new RiskAssessmentEntity();
    entity.setId(assessment.getId().value());
    entity.setTenantId(tenantId.value());
    entity.setAnalysisRunId(assessment.getAnalysisRunId().value());
    entity.setRiskScore(assessment.getScore().value());
    entity.setRiskLevel(assessment.getLevel().name());
    entity.setEvidenceState(assessment.getEvidenceState().name());
    entity.setAssessmentVersion(assessment.getAssessmentVersion());
    entity.setCalculatedAt(assessment.getCalculatedAt());

    List<RiskFactorEntity> factors = new ArrayList<>();
    for (RiskFactor factor : assessment.getFactors()) {
      RiskFactorEntity factorEntity = new RiskFactorEntity();
      factorEntity.setId(UUID.randomUUID());
      factorEntity.setTenantId(tenantId.value());
      factorEntity.setRiskAssessment(entity);
      factorEntity.setFactorType(factor.type().name());
      factorEntity.setContribution(factor.contribution());
      factorEntity.setExplanation(factor.explanation());
      factorEntity.setEvidenceReferenceIds(join(factor.evidenceReferences()));
      factors.add(factorEntity);
    }
    entity.setFactors(factors);
    return entity;
  }

  static RiskAssessment toDomain(RiskAssessmentEntity entity) {
    List<RiskFactor> factors = new ArrayList<>();
    for (RiskFactorEntity factorEntity : entity.getFactors()) {
      factors.add(new RiskFactor(
          RiskFactorType.valueOf(factorEntity.getFactorType()),
          factorEntity.getContribution(),
          factorEntity.getExplanation(),
          split(factorEntity.getEvidenceReferenceIds())));
    }
    return new RiskAssessment(
        new RiskAssessmentId(entity.getId()),
        new AnalysisRunId(entity.getAnalysisRunId()),
        RiskScore.of(entity.getRiskScore()),
        RiskLevel.valueOf(entity.getRiskLevel()),
        factors,
        EvidenceState.valueOf(entity.getEvidenceState()),
        entity.getAssessmentVersion(),
        entity.getCalculatedAt());
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