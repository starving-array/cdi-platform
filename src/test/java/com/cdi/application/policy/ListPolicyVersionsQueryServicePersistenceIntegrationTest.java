package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.port.in.ListPolicyVersionsQuery;
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
 * End-to-end UC-18 ListPolicyVersions integration test: real JPA adapter
 * (Testcontainers PostgreSQL) wired into {@link ListPolicyVersionsQueryService}.
 * Persists the tenant root (Organization, satisfying the V9 FK) and multiple
 * policy versions (ACTIVE + ARCHIVED) through the existing ports, then verifies
 * the full version listing with deterministic oldest→newest ordering and full
 * aggregate data, missing-tenant and cross-tenant {@code POLICY_NOT_FOUND}, and
 * ENGINEER / SYSTEM_WORKER role rejection (application-layer.md §6 UC-18,
 * policy-decision-domain.md §3). Each test seeds its own fresh tenant id to stay
 * isolated inside the shared (non-reset) test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class ListPolicyVersionsQueryServicePersistenceIntegrationTest {

  private static final Instant CREATED_AT = Instant.parse("2026-08-15T12:00:00Z");

  @Autowired
  private PolicyRepository policyRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);

  private ListPolicyVersionsQueryService service;

  @BeforeEach
  void setUp() {
    service = new ListPolicyVersionsQueryService(policyRepository);
  }

  @Test
  void listsPersistedVersionsWithFullAggregatesOrderedOldestFirst() {
    TenantId tenantId = seedTenant("ListPol Alpha");
    Policy archived = seedPolicy(tenantId, PolicyStatus.ARCHIVED, PolicyVersion.of("v2"),
        "2026-08-15T10:00:00Z");
    Policy active = seedPolicy(tenantId, PolicyStatus.ACTIVE, PolicyVersion.of("v3"),
        "2026-08-15T12:00:00Z");

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(active.getId()), tenantId, tenantAdmin);

    assertEquals(2, versions.size());
    assertEquals(archived.getId(), versions.get(0).getId());
    assertEquals(active.getId(), versions.get(1).getId());
    assertEquals(PolicyVersion.of("v2"), versions.get(0).getVersion());
    assertEquals(PolicyStatus.ARCHIVED, versions.get(0).getStatus());
    assertEquals(PolicyVersion.of("v3"), versions.get(1).getVersion());
    assertEquals(PolicyStatus.ACTIVE, versions.get(1).getStatus());
    assertEquals("payment-policy", versions.get(1).getName());
    assertEquals(1, versions.get(1).getRules().size());
    PolicyRule rule = versions.get(1).getRules().get(0);
    assertEquals("R1", rule.ruleId());
    assertEquals(DecisionOutcome.APPROVE, rule.outcome());
  }

  @Test
  void returnsSingleVersionWhenTenantHasOnlyOnePolicy() {
    TenantId tenantId = seedTenant("ListPol Beta");
    Policy active = seedPolicy(tenantId, PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        "2026-08-15T12:00:00Z");

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(active.getId()), tenantId, tenantAdmin);

    assertEquals(1, versions.size());
    assertEquals(active.getId(), versions.get(0).getId());
  }

  @Test
  void missingPolicyRaisesPolicyNotFound() {
    TenantId tenantId = seedTenant("ListPol Gamma");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListPolicyVersionsQuery(PolicyId.generate()), tenantId, tenantAdmin));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void crossTenantPolicyIsNotVisible() {
    TenantId tenantId = seedTenant("ListPol Delta");
    Policy seeded = seedPolicy(tenantId, PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        "2026-08-15T12:00:00Z");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> service.handle(
            new ListPolicyVersionsQuery(seeded.getId()), TenantId.generate(), tenantAdmin));

    assertEquals(ApplicationError.POLICY_NOT_FOUND, ex.getError());
  }

  @Test
  void nonTenantAdminRoleRejected() {
    TenantId tenantId = seedTenant("ListPol Epsilon");
    Policy seeded = seedPolicy(tenantId, PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
        "2026-08-15T12:00:00Z");

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
    TenantId tenantId = seedTenant("ListPol Zeta");
    Policy archived = Policy.restore(
        PolicyId.generate(), tenantId, "legacy-policy", "description",
        PolicyStatus.ARCHIVED, PolicyVersion.of("v2"),
        List.of(new PolicyRule("R9", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.BLOCK, List.of(), "Legacy rule")),
        CREATED_AT);
    policyRepository.save(archived);

    List<Policy> versions = service.handle(
        new ListPolicyVersionsQuery(archived.getId()), tenantId, tenantAdmin);

    assertEquals(1, versions.size());
    assertEquals(PolicyStatus.ARCHIVED, versions.get(0).getStatus());
    assertEquals(PolicyVersion.of("v2"), versions.get(0).getVersion());
    assertEquals("Legacy rule", versions.get(0).getRules().get(0).explanation());
  }

  private TenantId seedTenant(String name) {
    TenantId tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, name + "-" + UUID.randomUUID(), Instant.now()));
    return tenantId;
  }

  private Policy seedPolicy(TenantId tenantId, PolicyStatus status, PolicyVersion version,
      String createdAt) {
    Policy policy = new Policy(
        PolicyId.generate(), tenantId, "payment-policy",
        "Ensures payment changes are reviewed", status, version,
        List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(), "Requires human review")),
        Instant.parse(createdAt));
    return policyRepository.save(policy);
  }
}