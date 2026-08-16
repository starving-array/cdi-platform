package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.GetPolicyQuery;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.organization.domain.Organization;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.testconfig.PostgresTestContainerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end UC-16 GetPolicy integration test: real JPA adapter
 * (Testcontainers PostgreSQL) wired into {@link GetPolicyQueryService}.
 * Persists the tenant root (Organization, satisfying the V9 FK) and the
 * Policy through the existing ports, then verifies the full aggregate read
 * (policy + policy_rule rows) for both ENGINEER and TENANT_ADMIN actors,
 * archived-status round-trip, missing-tenant and cross-tenant
 * {@code POLICY_NOT_FOUND}, and SYSTEM_WORKER role rejection
 * (application-layer.md §6 UC-16, data-model.md §E). Each test seeds its own
 * tenant id to stay isolated inside the shared (non-reset) test database —
 * the partial {@code UNIQUE (tenant_id) WHERE status = 'ACTIVE'} (V9)
 * constraint allows at most one ACTIVE policy per tenant, so every tenant is
 * fresh.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class GetPolicyQueryServicePersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-15T12:00:00Z");

  @Autowired
  private PolicyRepository policyRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private final Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private GetPolicyQueryService service;

  @BeforeEach
  void setUp() {
    service = new GetPolicyQueryService(policyRepository);
  }

  @Test
  void returnsFullAggregateForEngineer() {
    TenantId tenantId = seedTenant("GetPol Alpha");
    Policy seeded = seedPolicy(tenantId);

    Policy result = service.handle(
        new GetPolicyQuery(seeded.getId()), tenantId, engineer);

    assertEquals(seeded.getId(), result.getId());
    assertEquals(tenantId, result.getTenantId());
    assertEquals("payment-policy", result.getName());
    assertEquals("Ensures payment changes are reviewed", result.getDescription());
    assertEquals(PolicyVersion.of("v1"), result.getVersion());
    assertEquals(PolicyStatus.ACTIVE, result.getStatus());
    assertEquals(CREATED_AT, result.getCreatedAt());
    assertEquals(1, result.getRules().size());
    PolicyRule rule = result.getRules().get(0);
    assertEquals("R1", rule.ruleId());
    assertEquals(DecisionOutcome.APPROVE, rule.outcome());
  }

  @Test
  void returnsFullAggregateForTenantAdmin() {
    TenantId tenantId = seedTenant("GetPol Beta");
    Policy seeded = seedPolicy(tenantId);

    Policy result = service.handle(
        new GetPolicyQuery(seeded.getId()), tenantId, tenantAdmin);

    assertEquals(seeded.getId(), result.getId());
    assertEquals("payment-policy", result.getName());
    assertEquals(PolicyVersion.of("v1"), result.getVersion());
    assertEquals(1, result.getRules().size());
  }

  @Test
  void preservesArchivedStatus() {
    TenantId tenantId = seedTenant("GetPol Gamma");
    Policy archived = Policy.restore(
        PolicyId.generate(), tenantId, "legacy-policy", "description",
        PolicyStatus.ARCHIVED, PolicyVersion.of("v2"),
        List.of(new PolicyRule("R9", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.BLOCK, List.of(), "Legacy rule")),
        CREATED_AT);
    policyRepository.save(archived);

    Policy result = service.handle(
        new GetPolicyQuery(archived.getId()), tenantId, engineer);

    assertEquals(PolicyStatus.ARCHIVED, result.getStatus());
    assertEquals(PolicyVersion.of("v2"), result.getVersion());
  }

  @Test
  void missingPolicyRaisesPolicyNotFound() {
    TenantId tenantId = seedTenant("GetPol Delta");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetPolicyQuery(PolicyId.generate()), tenantId, engineer));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantPolicyIsNotVisible() {
    TenantId tenantId = seedTenant("GetPol Epsilon");
    Policy seeded = seedPolicy(tenantId);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new GetPolicyQuery(seeded.getId()), TenantId.generate(), tenantAdmin));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void systemWorkerRejected() {
    TenantId tenantId = seedTenant("GetPol Zeta");
    Policy seeded = seedPolicy(tenantId);

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(new GetPolicyQuery(seeded.getId()), tenantId,
            new Actor("worker-1", Actor.Role.SYSTEM_WORKER)));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  private TenantId seedTenant(String name) {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, name + "-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }

  private Policy seedPolicy(TenantId tenantId) {
    Policy policy = new Policy(
        PolicyId.generate(), tenantId, "payment-policy",
        "Ensures payment changes are reviewed", PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(), "Requires human review")),
        CREATED_AT);
    return policyRepository.save(policy);
  }
}
