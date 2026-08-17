package com.cdi.decision.adapter.out.persistence;

import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RiskAssessmentId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.decision.domain.DecisionRecord;
import com.cdi.decision.domain.HumanOverride;
import com.cdi.decision.domain.RequiredAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps between the {@code DecisionRecord} aggregate and its persistence
 * representation (parent + reasons).
 *
 * <p>Horizontal conversion only: the aggregate's tenant scope is supplied from
 * the port call because {@code TenantId} does not live on the aggregate
 * (data-model.md §2); immutable {@code DecisionReason} children flatten to the
 * {@code decision_reason} table with evidence references joined as a
 * comma-separated UUID list, and {@code RequiredAction} enums flatten to a CSV
 * column on the parent (children/payloads are never queried by those columns,
 * data-model.md §7).
 */
final class DecisionRecordMapper {

  private DecisionRecordMapper() {
    // utility class
  }

  static DecisionRecordEntity toEntity(TenantId tenantId, DecisionRecord record) {
    DecisionRecordEntity entity = new DecisionRecordEntity();
    entity.setId(record.getId().value());
    entity.setTenantId(tenantId.value());
    entity.setAnalysisRunId(record.getAnalysisRunId().value());
    entity.setRiskAssessmentId(record.getRiskAssessmentId().value());
    entity.setPolicyId(record.getPolicyId().value());
    entity.setPolicyVersion(record.getPolicyVersion());
    entity.setOutcome(record.getOriginalOutcome().name());
    entity.setRequiredActions(joinActions(record.getRequiredActions()));
    entity.setGeneratedAt(record.getGeneratedAt());

    List<DecisionReasonEntity> reasons = new ArrayList<>();
    for (DecisionReason reason : record.getReasons()) {
      DecisionReasonEntity reasonEntity = new DecisionReasonEntity();
      reasonEntity.setId(UUID.randomUUID());
      reasonEntity.setTenantId(tenantId.value());
      reasonEntity.setDecisionRecord(entity);
      reasonEntity.setExplanation(reason.explanation());
      reasonEntity.setRuleId(reason.ruleId());
      reasonEntity.setEvidenceReferenceIds(join(reason.evidenceReferences()));
      reasons.add(reasonEntity);
    }
    entity.setReasons(reasons);

    if (record.getOverride().isPresent()) {
      HumanOverride override = record.getOverride().get();
      HumanOverrideEntity overrideEntity = new HumanOverrideEntity();
      overrideEntity.setId(UUID.randomUUID());
      overrideEntity.setTenantId(tenantId.value());
      overrideEntity.setDecisionRecord(entity);
      overrideEntity.setOriginalOutcome(override.originalOutcome().name());
      overrideEntity.setNewOutcome(override.newOutcome().name());
      overrideEntity.setActorId(override.actorId());
      overrideEntity.setJustification(override.justification());
      overrideEntity.setOverrideAt(override.timestamp());
      overrideEntity.setCreatedAt(override.timestamp());
      overrideEntity.setUpdatedAt(override.timestamp());
      entity.setOverride(overrideEntity);
    }
    return entity;
  }

  static DecisionRecord toDomain(DecisionRecordEntity entity) {
    List<DecisionReason> reasons = new ArrayList<>();
    for (DecisionReasonEntity reasonEntity : entity.getReasons()) {
      reasons.add(new DecisionReason(
          reasonEntity.getExplanation(),
          reasonEntity.getRuleId(),
          split(reasonEntity.getEvidenceReferenceIds())));
    }
    DecisionRecord.Builder builder = DecisionRecord.builder()
        .id(new DecisionId(entity.getId()))
        .tenantId(new TenantId(entity.getTenantId()))
        .analysisRunId(new AnalysisRunId(entity.getAnalysisRunId()))
        .riskAssessmentId(new RiskAssessmentId(entity.getRiskAssessmentId()))
        .policyId(new PolicyId(entity.getPolicyId()))
        .policyVersion(entity.getPolicyVersion())
        .outcome(DecisionOutcome.valueOf(entity.getOutcome()))
        .reasons(reasons)
        .requiredActions(splitActions(entity.getRequiredActions()))
        .generatedAt(entity.getGeneratedAt());
    if (entity.getOverride() != null) {
      HumanOverrideEntity overrideEntity = entity.getOverride();
      builder.override(new HumanOverride(
          overrideEntity.getActorId(),
          DecisionOutcome.valueOf(overrideEntity.getOriginalOutcome()),
          DecisionOutcome.valueOf(overrideEntity.getNewOutcome()),
          overrideEntity.getJustification(),
          overrideEntity.getOverrideAt()));
    }
    return builder.build();
  }

  private static String joinActions(List<RequiredAction> actions) {
    if (actions == null || actions.isEmpty()) {
      return null;
    }
    return actions.stream().map(Enum::name).collect(Collectors.joining(","));
  }

  private static List<RequiredAction> splitActions(String joined) {
    if (joined == null || joined.isBlank()) {
      return List.of();
    }
    List<RequiredAction> actions = new ArrayList<>();
    for (String part : joined.split(",")) {
      if (!part.isBlank()) {
        actions.add(RequiredAction.valueOf(part.trim()));
      }
    }
    return actions;
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
