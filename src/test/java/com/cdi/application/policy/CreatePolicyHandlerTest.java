package com.cdi.application.policy;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.application.common.result.CreatePolicyResult;
import com.cdi.application.port.in.CreatePolicyCommand;
import com.cdi.application.port.out.PolicyRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.policy.domain.Policy;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyStatus;
import com.cdi.policy.domain.PolicyVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for UC-10 CreatePolicy. Uses a fake port only at the
 * application boundary; no Testcontainers, no Spring context, no persistence
 * is started. UC-10 emits no domain event, so (like UC-08/UC-09) there is no
 * event-publisher fake here.
 */
class CreatePolicyHandlerTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final Actor tenantAdmin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
  // Fresh per test method (JUnit Jupiter creates a new test instance per
  // method); all command(...) helpers below use this same tenantId so that
  // idempotency is exercised within one tenant scope.
  private final TenantId tenantId = TenantId.generate();

  private FakePolicyRepository policyRepository;
  private CreatePolicyHandler handler;

  @BeforeEach
  void setUp() {
    policyRepository = new FakePolicyRepository();
    handler = new CreatePolicyHandler(policyRepository, CLOCK);
  }

  @Test
  void successfulTenantAdminCreation() {
    CreatePolicyResult result = handler.handle(command("core-policy"));

    assertTrue(result.created());
    assertNotNull(result.policyId());
    assertEquals(1, policyRepository.savedPolicies.size());
  }

  @Test
  void generatedPolicyIdIsReturned() {
    CreatePolicyResult result = handler.handle(command("core-policy"));

    Policy saved = policyRepository.savedPolicies.get(0);
    assertEquals(saved.getId(), result.policyId());
    assertNotNull(result.policyId().value());
  }

  @Test
  void createdPolicyStartsActive() {
    handler.handle(command("core-policy"));

    Policy saved = policyRepository.savedPolicies.get(0);
    assertEquals(PolicyStatus.ACTIVE, saved.getStatus());
  }

  @Test
  void allFieldsPreservedOnCreation() {
    CreatePolicyCommand command = command("core-policy");
    handler.handle(command);

    Policy saved = policyRepository.savedPolicies.get(0);
    assertEquals(command.tenantId(), saved.getTenantId());
    assertEquals("core-policy", saved.getName());
    assertEquals("description", saved.getDescription());
    assertEquals(PolicyStatus.ACTIVE, saved.getStatus());
    assertEquals("v1", saved.getVersion().value());
    assertEquals(1, saved.getRules().size());
    assertEquals(NOW, saved.getCreatedAt());
  }

  @Test
  void unauthorizedActorRejected() {
    Actor engineer = new Actor("engineer-1", Actor.Role.ENGINEER);
    CreatePolicyCommand engineerCommand = command(engineer, "core-policy");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(engineerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
    assertTrue(policyRepository.savedPolicies.isEmpty());
  }

  @Test
  void systemWorkerRoleRejected() {
    Actor worker = new Actor("worker-1", Actor.Role.SYSTEM_WORKER);
    CreatePolicyCommand workerCommand = command(worker, "core-policy");

    ApplicationException ex = assertThrows(ApplicationException.class,
        () -> handler.handle(workerCommand));

    assertEquals(ApplicationError.UNAUTHORIZED, ex.getError());
  }

  @Test
  void invalidCommandRejectedByContract() {
    PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
    // blank name
    assertThrows(DomainException.class, () -> command("   "));
    // null tenant
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        null, "name", "description", PolicyVersion.of("v1"), List.of(rule),
        tenantAdmin, new IdempotencyKey("key-1")));
    // null version
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "description", null, List.of(rule),
        tenantAdmin, new IdempotencyKey("key-1")));
    // empty rules
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "description", PolicyVersion.of("v1"), List.of(),
        tenantAdmin, new IdempotencyKey("key-1")));
    // null actor
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "description", PolicyVersion.of("v1"), List.of(rule),
        null, new IdempotencyKey("key-1")));
    // null idempotencyKey
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "description", PolicyVersion.of("v1"), List.of(rule),
        tenantAdmin, null));
  }

  @Test
  void existingActivePolicyReusedIdempotently() {
    Policy existing = policyRepository.seed(tenantId);

    CreatePolicyResult result = handler.handle(command("core-policy"));

    assertFalse(result.created());
    assertEquals(existing.getId(), result.policyId());
    assertTrue(policyRepository.savedPolicies.isEmpty());
  }

  @Test
  void repeatedInvocationDoesNotSave() {
    handler.handle(command("core-policy"));
    assertEquals(1, policyRepository.savedPolicies.size());

    handler.handle(command("core-policy"));
    assertEquals(1, policyRepository.savedPolicies.size());
  }

  @Test
  void differentTenantsCreateDistinctPolicies() {
    TenantId tenantA = TenantId.generate();
    TenantId tenantB = TenantId.generate();

    handler.handle(new CreatePolicyCommand(
        tenantA, "policy-a", "", PolicyVersion.of("v1"), List.of(rule()),
        tenantAdmin, new IdempotencyKey("key-a")));
    handler.handle(new CreatePolicyCommand(
        tenantB, "policy-b", "", PolicyVersion.of("v1"), List.of(rule()),
        tenantAdmin, new IdempotencyKey("key-b")));

    assertEquals(2, policyRepository.savedPolicies.size());
  }

  @Test
  void duplicateLookupIsTenantScoped() {
    // An active policy under a different tenant does NOT match — it is a
    // distinct policy, not an idempotent reuse.
    TenantId otherTenant = TenantId.generate();
    policyRepository.seed(otherTenant);

    CreatePolicyResult result = handler.handle(command("core-policy"));

    assertTrue(result.created());
    assertEquals(1, policyRepository.savedPolicies.size());
  }

  @Test
  void persistenceFailurePropagates() {
    policyRepository.throwOnSave = true;

    assertThrows(RuntimeException.class,
        () -> handler.handle(command("core-policy")));
  }

  private PolicyRule rule() {
    return new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
  }

  private CreatePolicyCommand command(String name) {
    return command(tenantAdmin, name);
  }

  private CreatePolicyCommand command(Actor actor, String name) {
    return new CreatePolicyCommand(
        tenantId, name, "description", PolicyVersion.of("v1"),
        List.of(rule()), actor, new IdempotencyKey("header-key-1"));
  }

  private static class FakePolicyRepository implements PolicyRepository {
    final Map<UUID, Policy> activeByTenant = new HashMap<>();
    final List<Policy> savedPolicies = new ArrayList<>();
    boolean throwOnSave = false;

    @Override
    public Optional<Policy> findActiveByTenant(TenantId tenantId) {
      return Optional.ofNullable(activeByTenant.get(tenantId.value()));
    }

    @Override
    public Policy save(Policy policy) {
      if (throwOnSave) {
        throw new RuntimeException("persistence failure");
      }
      savedPolicies.add(policy);
      activeByTenant.put(policy.getTenantId().value(), policy);
      return policy;
    }

    Policy seed(TenantId tenantId) {
      Policy seeded = new Policy(
          PolicyId.generate(), tenantId, "existing-policy", "seeded",
          PolicyStatus.ACTIVE, PolicyVersion.of("v1"),
          List.of(new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
              DecisionOutcome.APPROVE, List.of(), "Existing")),
          NOW.minusSeconds(3600));
      activeByTenant.put(tenantId.value(), seeded);
      return seeded;
    }
  }
}
