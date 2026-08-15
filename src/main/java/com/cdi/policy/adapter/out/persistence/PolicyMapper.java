package com.cdi.policy.adapter.out.persistence;

import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps between the {@code Policy} aggregate and its persistence representation
 * (parent + rules).
 *
 * <p>The {@code Policy} aggregate carries its own {@code TenantId} directly
 * (it is a tenant-scoped child aggregate), so the conversion needs no separate
 * {@code tenantId} argument — the tenant scope is read/written from the
 * aggregate itself. Mirrors {@code RiskAssessmentMapper} for the parent + 1:N
 * child pattern.
 *
 * <p>The {@code updated_at} column is initialized to {@code createdAt} on
 * write; the domain Policy exposes no {@code updatedAt} field. On read the
 * entity's {@code updatedAt} is not surfaced to the domain — the aggregate is
 * rebuilt via {@link Policy#restore}.
 *
 * <p>The {@code PolicyRule} match sets ({@code Set<CriticalityTier>},
 * {@code Set<RiskLevel>}, {@code Set<EvidenceState>}) and the
 * {@code List<RequiredAction>} flatten to comma-separated enum-name CSVs, in a
 * stable ordering, to round-trip reliably (never-query-by-column child payload,
 * data-model.md §7 — same precedent as {@code risk_factor.evidence_reference_ids}).
 */
final class PolicyMapper {

  private PolicyMapper() {
    // utility class
  }

  static PolicyEntity toEntity(Policy policy) {
    PolicyEntity entity = new PolicyEntity();
    entity.setId(policy.getId().value());
    entity.setTenantId(policy.getTenantId().value());
    entity.setName(policy.getName());
    entity.setDescription(policy.getDescription());
    entity.setStatus(policy.getStatus().name());
    entity.setVersion(policy.getVersion().value());
    entity.setCreatedAt(policy.getCreatedAt());
    entity.setUpdatedAt(policy.getCreatedAt());

    List<PolicyRuleEntity> ruleEntities = new ArrayList<>();
    for (PolicyRule rule : policy.getRules()) {
      PolicyRuleEntity ruleEntity = new PolicyRuleEntity();
      ruleEntity.setId(UUID.randomUUID());
      ruleEntity.setTenantId(policy.getTenantId().value());
      ruleEntity.setPolicy(entity);
      ruleEntity.setRuleId(rule.ruleId());
      ruleEntity.setTargetTiers(joinEnums(rule.targetTiers(), CriticalityTier.class, CriticalityTier::name));
      ruleEntity.setTargetRiskLevels(joinEnums(rule.targetRiskLevels(), RiskLevel.class, RiskLevel::name));
      ruleEntity.setRequiredEvidenceStates(joinEnums(rule.requiredEvidenceStates(), EvidenceState.class, EvidenceState::name));
      ruleEntity.setOutcome(rule.outcome().name());
      ruleEntity.setActions(joinEnums(rule.actions(), RequiredAction.class, RequiredAction::name));
      ruleEntity.setExplanation(rule.explanation());
      ruleEntities.add(ruleEntity);
    }
    entity.setRules(ruleEntities);
    return entity;
  }

  static Policy toDomain(PolicyEntity entity) {
    List<PolicyRule> rules = new ArrayList<>();
    for (PolicyRuleEntity ruleEntity : entity.getRules()) {
      rules.add(new PolicyRule(
          ruleEntity.getRuleId(),
          splitEnums(ruleEntity.getTargetTiers(), CriticalityTier::valueOf, CriticalityTier.class),
          splitEnums(ruleEntity.getTargetRiskLevels(), RiskLevel::valueOf, RiskLevel.class),
          splitEnums(ruleEntity.getRequiredEvidenceStates(), EvidenceState::valueOf, EvidenceState.class),
          DecisionOutcome.valueOf(ruleEntity.getOutcome()),
          splitEnumsToList(ruleEntity.getActions(), RequiredAction::valueOf),
          ruleEntity.getExplanation()));
    }
    return Policy.restore(
        new PolicyId(entity.getId()),
        new TenantId(entity.getTenantId()),
        entity.getName(),
        entity.getDescription(),
        PolicyStatus.valueOf(entity.getStatus()),
        PolicyVersion.of(entity.getVersion()),
        rules,
        entity.getCreatedAt());
  }

  private static <E extends Enum<E>> String joinEnums(Collection<? extends E> values, Class<E> type, Function<E, String> nameFn) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.stream()
        .map(nameFn)
        .sorted()
        .collect(Collectors.joining(","));
  }

  private static <E extends Enum<E>> Set<E> splitEnums(String joined, Function<String, E> valueOf, Class<E> type) {
    if (joined == null || joined.isBlank()) {
      return java.util.Collections.emptySet();
    }
    Set<E> result = new LinkedHashSet<>();
    for (String part : joined.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(valueOf.apply(trimmed));
      }
    }
    return result;
  }

  private static <E extends Enum<E>> List<E> splitEnumsToList(String joined, Function<String, E> valueOf) {
    if (joined == null || joined.isBlank()) {
      return java.util.Collections.emptyList();
    }
    List<E> result = new ArrayList<>();
    for (String part : joined.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(valueOf.apply(trimmed));
      }
    }
    return result;
  }
}
