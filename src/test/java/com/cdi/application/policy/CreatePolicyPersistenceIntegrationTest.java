package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.result.CreatePolicyResult;
import com.cdi.application.port.in.CreatePolicyCommand;
import com.cdi.application.port.out.OrganizationRepository;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.organization.domain.Organization;
import com.cdi.policy.adapter.out.persistence.PolicyJpaRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end UC-10 integration test: real {@code PolicyRepository} JPA
 * adapter (Testcontainers PostgreSQL) wired into {@code CreatePolicyHandler}.
 * UC-10 emits no domain event, so (like UC-08/UC-09) there is no
 * event-publisher fake here. Verifies the full persist path (policy +
 * policy_rule rows), cross-process idempotent reuse, and that the one-active-
 * policy-per-tenant invariant never creates a second active row
 * (application-layer.md §6/§10, V9 partial unique index).
 *
 * <p>A real {@code Organization} (tenant row) is persisted before each test so
 * the V9 {@code FK(tenant_id) REFERENCES tenant(id)} constraint is satisfied.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfiguration.class)
class CreatePolicyPersistenceIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Autowired
  private PolicyRepository policyRepository;

  @Autowired
  private OrganizationRepository organizationRepository;

  @Autowired
  private PolicyJpaRepository policyRowCount;

  private TenantId tenantId;
  private CreatePolicyHandler handler;

  @BeforeEach
  void setUp() {
    tenantId = TenantId.generate();
    organizationRepository.save(new Organization(
        tenantId, "policy-e2e-" + UUID.randomUUID(), Instant.now()));
    handler = new CreatePolicyHandler(policyRepository, CLOCK);
  }

  @Test
  void firstInvocationCreatesPolicy() {
    CreatePolicyCommand command = command("core-policy");

    CreatePolicyResult result = handler.handle(command);

    assertTrue(result.created());
    assertEquals(
        1, policyRowCount.countByTenantIdAndStatus(
            tenantId.value(), PolicyStatus.ACTIVE.name()));

    Policy persisted = policyRepository.findActiveByTenant(tenantId).orElseThrow();
    assertEquals(result.policyId(), persisted.getId());
    assertEquals(PolicyStatus.ACTIVE, persisted.getStatus());
    assertEquals(NOW, persisted.getCreatedAt());
  }

  @Test
  void secondInvocationReturnsExistingPolicyWithCreatedFalse() {
    CreatePolicyCommand command = command("core-policy");

    CreatePolicyResult first = handler.handle(command);
    CreatePolicyResult second = handler.handle(command);

    assertTrue(first.created());
    assertFalse(second.created());
    assertEquals(first.policyId(), second.policyId());
  }

  @Test
  void repeatedInvocationLeavesExactlyOneActiveRow() {
    CreatePolicyCommand command = command("core-policy");

    handler.handle(command);
    handler.handle(command);
    handler.handle(command);

    assertEquals(
        1, policyRowCount.countByTenantIdAndStatus(
            tenantId.value(), PolicyStatus.ACTIVE.name()));
  }

  @Test
  void rulesPersistThroughHandler() {
    handler.handle(command("core-policy"));

    Policy persisted = policyRepository.findActiveByTenant(tenantId).orElseThrow();
    assertEquals(1, persisted.getRules().size());
    PolicyRule rule = persisted.getRules().get(0);
    assertEquals("R1", rule.ruleId());
    assertEquals(DecisionOutcome.APPROVE, rule.outcome());
    assertEquals("Requires human review", rule.explanation());
  }

  private CreatePolicyCommand command(String name) {
    return new CreatePolicyCommand(
        tenantId, name, "description", PolicyVersion.of("v1"),
        List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
            DecisionOutcome.APPROVE, List.of(), "Requires human review")),
        new Actor("admin-1", Actor.Role.TENANT_ADMIN),
        new IdempotencyKey("header-key-1"));
  }
}
