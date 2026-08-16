package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetPolicyQuery;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for UC-16 GetPolicy. Uses a fake port only at the application
 * boundary; no Testcontainers, no Spring context, no persistence is started.
 * Verifies the full aggregate read for both ENGINEER and TENANT_ADMIN actors,
 * archived-status preservation, missing-tenant and cross-tenant
 * {@code POLICY_NOT_FOUND}, and SYSTEM_WORKER role rejection.
 */
class GetPolicyQueryServiceTest {

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakePolicyRepository policyRepository;
  private GetPolicyQueryService service;

  @BeforeEach
  void setUp() {
    policyRepository = new FakePolicyRepository();
    service = new GetPolicyQueryService(policyRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    Policy seeded = policyRepository.seed(tenantId, PolicyStatus.ACTIVE);

    Policy result = service.handle(
        new GetPolicyQuery(seeded.getId()), tenantId, engineer);

    assertEquals(seeded.getId(), result.getId());
    assertEquals(tenantId, result.getTenantId());
    assertEquals("payment-policy", result.getName());
    assertEquals("Ensures payment changes are reviewed", result.getDescription());
    assertEquals(PolicyVersion.of("v1"), result.getVersion());
    assertEquals(PolicyStatus.ACTIVE, result.getStatus());
    assertEquals(seeded.getCreatedAt(), result.getCreatedAt());
    assertEquals(1, result.getRules().size());
    assertEquals("R1", result.getRules().get(0).ruleId());
    assertEquals(DecisionOutcome.APPROVE, result.getRules().get(0).outcome());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    Policy seeded = policyRepository.seed(tenantId, PolicyStatus.ACTIVE);

    Policy result = service.handle(
        new GetPolicyQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), result.getId());
    assertEquals("payment-policy", result.getName());
    assertEquals(1, result.getRules().size());
  }

  @Test
  void preservesArchivedStatus() {
    Policy seeded = policyRepository.seed(tenantId, PolicyStatus.ARCHIVED);

    Policy result = service.handle(
        new GetPolicyQuery(seeded.getId()), tenantId, engineer);

    assertEquals(PolicyStatus.ARCHIVED, result.getStatus());
  }

  @Test
  void missingPolicyRaisesPolicyNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetPolicyQuery(PolicyId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantPolicyIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    Policy seeded = policyRepository.seed(tenantB, PolicyStatus.ACTIVE);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetPolicyQuery(seeded.getId()), tenantId, engineer));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    Policy seeded = policyRepository.seed(tenantId, PolicyStatus.ACTIVE);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetPolicyQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private static class FakePolicyRepository implements PolicyRepository {
    final Map<PolicyId, Policy> byId = new HashMap<>();

    Policy seed(TenantId tenantId, PolicyStatus status) {
      Policy policy = new Policy(
          PolicyId.generate(), tenantId, "payment-policy",
          "Ensures payment changes are reviewed", status, PolicyVersion.of("v1"),
          List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
              DecisionOutcome.APPROVE, List.of(), "Requires human review")),
          Instant.parse("2026-08-15T12:00:00Z"));
      byId.put(policy.getId(), policy);
      return policy;
    }

    @Override
    public Optional<Policy> findActiveByTenant(TenantId tenantId) {
      return byId.values().stream()
          .filter(p -> p.getTenantId().equals(tenantId)
              && p.getStatus() == PolicyStatus.ACTIVE)
          .findFirst();
    }

    @Override
    public Optional<Policy> findByTenantIdAndId(TenantId tenantId, PolicyId policyId) {
      return byId.values().stream()
          .filter(p -> p.getTenantId().equals(tenantId) && p.getId().equals(policyId))
          .findFirst();
    }

    @Override
    public Policy save(Policy policy) {
      byId.put(policy.getId(), policy);
      return policy;
    }
  }
}