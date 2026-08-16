package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListPolicyVersionsQuery;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for UC-18 ListPolicyVersions. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. Verifies the full version listing for the TENANT_ADMIN actor
 * (ACTIVE + ARCHIVED, oldest→newest), missing-tenant and cross-tenant
 * {@code POLICY_NOT_FOUND}, and ENGINEER / SYSTEM_WORKER role rejection.
 */
class ListPolicyVersionsQueryServiceTest {

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  private final TenantId tenantId = TenantId.generate();

  private FakePolicyRepository policyRepository;
  private ListPolicyVersionsQueryService service;

  @BeforeEach
  void setUp() {
    policyRepository = new FakePolicyRepository();
    service = new ListPolicyVersionsQueryService(policyRepository);
  }

  @Test
  void returnsAllVersionsWithFullAggregatesOrderedOldestFirst() {
    Policy active = policyRepository.seed(tenantId, PolicyStatus.ACTIVE,
        PolicyVersion.of("v1"), "2026-08-15T12:00:00Z");
    Policy archived = policyRepository.seed(tenantId, PolicyStatus.ARCHIVED,
        PolicyVersion.of("v2"), "2026-08-15T10:00:00Z");

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(active.getId()), tenantId, tenantAdmin);

    assertEquals(2, versions.size());
    assertEquals(archived.getId(), versions.get(0).getId());
    assertEquals(active.getId(), versions.get(1).getId());
    assertEquals(PolicyVersion.of("v2"), versions.get(0).getVersion());
    assertEquals(PolicyStatus.ARCHIVED, versions.get(0).getStatus());
    assertEquals(PolicyVersion.of("v1"), versions.get(1).getVersion());
    assertEquals(PolicyStatus.ACTIVE, versions.get(1).getStatus());
    assertEquals(1, versions.get(1).getRules().size());
    assertEquals("R1", versions.get(1).getRules().get(0).ruleId());
  }

  @Test
  void returnsSingleVersionWhenTenantHasOnlyOnePolicy() {
    Policy active = policyRepository.seed(tenantId, PolicyStatus.ACTIVE,
        PolicyVersion.of("v1"), "2026-08-15T12:00:00Z");

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(active.getId()), tenantId, tenantAdmin);

    assertEquals(1, versions.size());
    assertEquals(active.getId(), versions.get(0).getId());
  }

  @Test
  void missingPolicyRaisesPolicyNotFound() {
    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListPolicyVersionsQuery(PolicyId.generate()), tenantId, tenantAdmin));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantPolicyIsNotVisible() {
    TenantId tenantB = TenantId.generate();
    Policy seeded = policyRepository.seed(tenantB, PolicyStatus.ACTIVE,
        PolicyVersion.of("v1"), "2026-08-15T12:00:00Z");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListPolicyVersionsQuery(seeded.getId()), tenantId, tenantAdmin));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void nonTenantAdminRoleRejected() {
    Policy seeded = policyRepository.seed(tenantId, PolicyStatus.ACTIVE,
        PolicyVersion.of("v1"), "2026-08-15T12:00:00Z");

    ApplicationException engineerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new ListPolicyVersionsQuery(seeded.getId()), tenantId,
            new Actor("engineer-1", Actor.Role.ENGINEER)));
    assertEquals(ApplicationError.UNAUTHORIZED, engineerEx.getError());

    ApplicationException workerEx = assertThrows(ApplicationException.class,
        () -> service.handle(new ListPolicyVersionsQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));
    assertEquals(ApplicationError.UNAUTHORIZED, workerEx.getError());
  }

  @Test
  void preservesArchivedVersionDataThroughListing() {
    Policy archived = policyRepository.seed(tenantId, PolicyStatus.ARCHIVED,
        PolicyVersion.of("v3"), "2026-08-15T08:00:00Z");

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(archived.getId()), tenantId, tenantAdmin);

    assertEquals(1, versions.size());
    assertEquals(PolicyStatus.ARCHIVED, versions.get(0).getStatus());
    assertEquals(PolicyVersion.of("v3"), versions.get(0).getVersion());
    assertEquals("legacy-policy", versions.get(0).getName());
    assertEquals("Requires human review", versions.get(0).getRules().get(0).explanation());
  }

  private static class FakePolicyRepository implements PolicyRepository {
    final Map<PolicyId, Policy> byId = new HashMap<>();

    Policy seed(TenantId tenantId, PolicyStatus status, PolicyVersion version, String createdAt) {
      Policy policy = new Policy(
          PolicyId.generate(), tenantId, "legacy-policy",
          "Historical version", status, version,
          List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
              DecisionOutcome.BLOCK, List.of(), "Requires human review")),
          Instant.parse(createdAt));
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
    public List<Policy> findAllByTenantId(TenantId tenantId) {
      return byId.values().stream()
          .filter(p -> p.getTenantId().equals(tenantId))
          .sorted((a, b) -> {
            int byTime = a.getCreatedAt().compareTo(b.getCreatedAt());
            return byTime != 0 ? byTime : a.getId().value().compareTo(b.getId().value());
          })
          .toList();
    }

    @Override
    public Policy save(Policy policy) {
      byId.put(policy.getId(), policy);
      return policy;
    }
  }
}