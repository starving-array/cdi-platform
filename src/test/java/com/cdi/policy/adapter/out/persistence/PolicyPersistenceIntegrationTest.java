package com.cdi.policy.adapter.out.persistence;

import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.organization.domain.Organization;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code policy}/{@code policy_rule} table adapter
 * against a real PostgreSQL Testcontainer. Scenarios mirror the UC-10
 * persistence contract (application-layer.md §6, data-model.md §E, V9
 * migration).
 *
 * <p>Because V9 declares {@code FK(tenant_id) REFERENCES tenant(id)}, every
 * policy row requires a pre-existing tenant row. A real {@code Organization}
 * is persisted via the existing {@link OrganizationRepository} adapter before
 * each test (no direct manipulation of the {@code tenant} table).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class PolicyPersistenceIntegrationTest {

  @Autowired
  private PolicyRepository adapter;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private PolicyJpaRepository policyRowCount;

  @Test
  void saveAndFindActiveRoundTripsAllFields() {
    TenantId tenantId = createTenant();
    Instant now = Instant.parse("2026-08-15T12:00:00Z");
    Policy policy = new Policy(
        PolicyId.generate(), tenantId, "core-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), now);

    adapter.save(policy);

    Policy loaded = adapter.findActiveByTenant(tenantId).orElseThrow();
    assertEquals(policy.getId(), loaded.getId());
    assertEquals(tenantId, loaded.getTenantId());
    assertEquals("core-policy", loaded.getName());
    assertEquals("desc", loaded.getDescription());
    assertEquals(PolicyStatus.ACTIVE, loaded.getStatus());
    assertEquals("v1", loaded.getVersion().value());
    assertEquals(1, loaded.getRules().size());
    assertEquals(now, loaded.getCreatedAt());
  }

  @Test
  void findActiveByTenantReturnsEmptyWhenAbsent() {
    TenantId tenantId = createTenant();

    Optional<Policy> result = adapter.findActiveByTenant(tenantId);

    assertTrue(result.isEmpty());
  }

  @Test
  void onlyActivePolicyReturnedByFindActive() {
    TenantId tenantId = createTenant();
    Policy archived = new Policy(
        PolicyId.generate(), tenantId, "archived-policy", "desc",
        PolicyStatus.ARCHIVED, PolicyVersion.of("v1"), List.of(rule()), Instant.now());
    adapter.save(archived);

    Optional<Policy> result = adapter.findActiveByTenant(tenantId);

    assertTrue(result.isEmpty());
  }

  @Test
  void archivedAndActivePoliciesCoexistForSameTenant() {
    TenantId tenantId = createTenant();
    Policy archived = new Policy(
        PolicyId.generate(), tenantId, "archived-policy", "desc",
        PolicyStatus.ARCHIVED, PolicyVersion.of("v1"), List.of(rule()), Instant.now());
    adapter.save(archived);

    Policy active = new Policy(
        PolicyId.generate(), tenantId, "core-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v2"), List.of(rule()), Instant.now());
    adapter.save(active);

    Policy loaded = adapter.findActiveByTenant(tenantId).orElseThrow();
    assertEquals(active.getId(), loaded.getId());
    assertEquals(2, policyRowCount.countByTenantId(tenantId.value()));
  }

  @Test
  void duplicateActivePolicyViolatesPartialUniqueIndex() {
    TenantId tenantId = createTenant();
    Policy first = new Policy(
        PolicyId.generate(), tenantId, "core-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now());
    adapter.save(first);

    Policy duplicate = new Policy(
        PolicyId.generate(), tenantId, "other-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(duplicate));
  }

  @Test
  void rulesRoundTripViaRestore() {
    TenantId tenantId = createTenant();
    Instant now = Instant.parse("2026-08-15T12:00:00Z");
    PolicyRule rule = new PolicyRule(
        "R1",
        Set.of(CriticalityTier.TIER_0, CriticalityTier.TIER_1),
        Set.of(RiskLevel.HIGH, RiskLevel.CRITICAL),
        Set.of(EvidenceState.EVIDENCE_AVAILABLE),
        DecisionOutcome.REVIEW_REQUIRED,
        List.of(RequiredAction.HUMAN_REVIEW, RequiredAction.SECURITY_REVIEW),
        "Requires human review");
    Policy policy = Policy.restore(
        PolicyId.generate(), tenantId, "rules-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule), now);

    adapter.save(policy);

    Policy loaded = adapter.findActiveByTenant(tenantId).orElseThrow();
    assertEquals(policy.getId(), loaded.getId());
    assertEquals(PolicyStatus.ACTIVE, loaded.getStatus());
    assertEquals(now, loaded.getCreatedAt());
    assertEquals(1, loaded.getRules().size());

    PolicyRule loadedRule = loaded.getRules().get(0);
    assertEquals("R1", loadedRule.ruleId());
    assertEquals(Set.of(CriticalityTier.TIER_0, CriticalityTier.TIER_1),
        loadedRule.targetTiers());
    assertEquals(Set.of(RiskLevel.HIGH, RiskLevel.CRITICAL),
        loadedRule.targetRiskLevels());
    assertEquals(Set.of(EvidenceState.EVIDENCE_AVAILABLE),
        loadedRule.requiredEvidenceStates());
    assertEquals(DecisionOutcome.REVIEW_REQUIRED, loadedRule.outcome());
    assertEquals(List.of(RequiredAction.HUMAN_REVIEW, RequiredAction.SECURITY_REVIEW),
        loadedRule.actions());
    assertEquals("Requires human review", loadedRule.explanation());
  }

  @Test
  void tenantIsolationPreventsCrossTenantLookup() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();
    Policy policy = new Policy(
        PolicyId.generate(), tenantA, "shared-name", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now());
    adapter.save(policy);

    Optional<Policy> onTenantB = adapter.findActiveByTenant(tenantB);
    assertTrue(onTenantB.isEmpty());

    Policy onTenantA = adapter.findActiveByTenant(tenantA).orElseThrow();
    assertEquals(policy.getId(), onTenantA.getId());
  }

  @Test
  void activePoliciesAllowedAcrossDifferentTenants() {
    TenantId tenantA = createTenant();
    TenantId tenantB = createTenant();

    adapter.save(new Policy(
        PolicyId.generate(), tenantA, "shared-name", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now()));
    adapter.save(new Policy(
        PolicyId.generate(), tenantB, "shared-name", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now()));

    Policy a = adapter.findActiveByTenant(tenantA).orElseThrow();
    Policy b = adapter.findActiveByTenant(tenantB).orElseThrow();
    assertEquals("shared-name", a.getName());
    assertEquals("shared-name", b.getName());
  }

  @Test
  void nonExistentTenantRejectedByForeignKey() {
    TenantId orphanTenant = TenantId.generate();
    Policy policy = new Policy(
        PolicyId.generate(), orphanTenant, "orphan-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now());

    assertThrows(DataIntegrityViolationException.class, () -> adapter.save(policy));
  }

  @Test
  void v9MigrationAppliesAndHibernateValidationPasses() {
    // Hibernate ddl-auto: validate runs at context startup. If the
    // PolicyEntity/PolicyRuleEntity fields did not exactly match the V9
    // columns, the @SpringBootTest context would fail to load and this test
    // (and the rest of the suite sharing the context) would not execute.
    // Reaching this assertion with a counted row confirms the migration
    // applied and the entity validates.
    TenantId tenantId = createTenant();
    adapter.save(new Policy(
        PolicyId.generate(), tenantId, "validate-policy", "desc",
        PolicyStatus.ACTIVE, PolicyVersion.of("v1"), List.of(rule()), Instant.now()));

    assertTrue(policyRowCount.countByTenantId(tenantId.value()) >= 1);
  }

  /**
   * A minimal valid rule (empty match sets evaluate to "matches everything").
   */
  private PolicyRule rule() {
    return new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
  }

  /**
   * Persists a real tenant row so the policy FK is satisfied. Uses a unique
   * organization name per call to avoid collisions with rows left by other
   * tests (the existing org-integration tests do not clean up between tests).
   */
  private TenantId createTenant() {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "policy-test-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }
}
