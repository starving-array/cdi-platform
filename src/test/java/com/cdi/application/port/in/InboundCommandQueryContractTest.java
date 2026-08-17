package com.cdi.application.port.in;

import com.cdi.application.common.Actor;
import com.cdi.application.common.IdempotencyKey;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.ChangeId;
import com.cdi.common.domain.id.DecisionId;
import com.cdi.common.domain.id.PolicyId;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.RequiredAction;
import com.cdi.policy.domain.PolicyRule;
import com.cdi.policy.domain.PolicyVersion;
import com.cdi.repository.domain.Repository;
import com.cdi.risk.domain.EvidenceState;
import com.cdi.risk.domain.RiskLevel;
import com.cdi.systemcontext.domain.CriticalityTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundCommandQueryContractTest {

  private final Actor actor = new Actor("user-1", Actor.Role.ENGINEER);
  private final IdempotencyKey key = new IdempotencyKey("key-1");

  @Test
  void shouldConstructProposeChangeCommand() {
    ProposeChangeCommand command = new ProposeChangeCommand(
        TenantId.generate(), RepositoryId.generate(), "PR-42", "abc123",
        "main", "Add feature", "desc", "author", actor, key);

    assertEquals("PR-42", command.providerChangeId());
    assertEquals("abc123", command.commitSha());
    assertEquals(actor, command.actor());
    assertEquals(key, command.idempotencyKey());
  }

  @Test
  void shouldRejectInvalidProposeChangeCommand() {
    assertThrows(DomainException.class, () -> new ProposeChangeCommand(
        null, RepositoryId.generate(), "PR-42", "abc123", "main",
        "title", "desc", "author", actor, key));
    assertThrows(DomainException.class, () -> new ProposeChangeCommand(
        TenantId.generate(), RepositoryId.generate(), "PR-42", "   ",
        "main", "title", "desc", "author", actor, key));
  }

  @Test
  void shouldConstructRequestAnalysisCommand() {
    TenantId tenantId = TenantId.generate();
    ChangeId changeId = ChangeId.generate();
    RequestAnalysisCommand command =
        new RequestAnalysisCommand(tenantId, changeId, "def456", actor, key);

    assertEquals(tenantId, command.tenantId());
    assertEquals(changeId, command.changeId());
    assertEquals("def456", command.commitSha());
    assertEquals(actor, command.actor());
  }

  @Test
  void shouldRejectInvalidRequestAnalysisCommand() {
    ChangeId changeId = ChangeId.generate();
    assertThrows(DomainException.class,
        () -> new RequestAnalysisCommand(null, changeId, "def456", actor, key));
    assertThrows(DomainException.class,
        () -> new RequestAnalysisCommand(TenantId.generate(), null, "def456", actor, key));
    assertThrows(DomainException.class,
        () -> new RequestAnalysisCommand(TenantId.generate(), changeId, "", actor, key));
  }

  @Test
  void shouldConstructWorkerCommands() {
    AnalysisRunId runId = AnalysisRunId.generate();

    AnalyzeChangeCommand analyze = new AnalyzeChangeCommand(runId);
    GenerateDecisionCommand generate = new GenerateDecisionCommand(runId);

    assertEquals(runId, analyze.analysisRunId());
    assertEquals(runId, generate.analysisRunId());
    assertThrows(DomainException.class, () -> new AnalyzeChangeCommand(null));
    assertThrows(DomainException.class, () -> new GenerateDecisionCommand(null));
  }

  @Test
  void shouldConstructP0Queries() {
    ChangeId changeId = ChangeId.generate();
    AnalysisRunId runId = AnalysisRunId.generate();
    TenantId tenantId = TenantId.generate();
    RepositoryId repositoryId = RepositoryId.generate();
    ServiceId serviceId = ServiceId.generate();
    PolicyId policyId = PolicyId.generate();

    assertEquals(changeId, new GetChangeQuery(changeId).changeId());
    assertEquals(runId, new GetAnalysisRunQuery(runId).analysisRunId());
    assertEquals(tenantId, new GetOrganizationQuery(tenantId).tenantId());
    assertEquals(repositoryId, new GetRepositoryQuery(repositoryId).repositoryId());
    assertEquals(serviceId, new GetServiceQuery(serviceId).serviceId());
    assertEquals(policyId, new GetPolicyQuery(policyId).policyId());
    assertEquals(changeId, new ListAnalysisRunsQuery(changeId).changeId());
    assertEquals(policyId, new ListPolicyVersionsQuery(policyId).policyId());
  }

  @Test
  void shouldRejectNullIdsInQueries() {
    assertThrows(DomainException.class, () -> new GetChangeQuery(null));
    assertThrows(DomainException.class, () -> new GetAnalysisRunQuery(null));
    assertThrows(DomainException.class, () -> new GetOrganizationQuery(null));
    assertThrows(DomainException.class, () -> new GetRepositoryQuery(null));
    assertThrows(DomainException.class, () -> new GetServiceQuery(null));
    assertThrows(DomainException.class, () -> new GetPolicyQuery(null));
    assertThrows(DomainException.class, () -> new ListAnalysisRunsQuery(null));
    assertThrows(DomainException.class, () -> new ListPolicyVersionsQuery(null));
  }

  @Test
  void shouldConstructGetChangeBySourceQuery() {
    TenantId tenantId = TenantId.generate();
    RepositoryId repositoryId = RepositoryId.generate();
    GetChangeBySourceQuery query =
        new GetChangeBySourceQuery(tenantId, repositoryId, "PR-42");

    assertEquals(tenantId, query.tenantId());
    assertEquals(repositoryId, query.repositoryId());
    assertEquals("PR-42", query.providerChangeId());
  }

  @Test
  void shouldTrimProviderChangeIdInGetChangeBySourceQuery() {
    GetChangeBySourceQuery query = new GetChangeBySourceQuery(
        TenantId.generate(), RepositoryId.generate(), "  PR-42  ");

    assertEquals("PR-42", query.providerChangeId());
  }

  @Test
  void shouldRejectInvalidGetChangeBySourceQuery() {
    assertThrows(DomainException.class, () -> new GetChangeBySourceQuery(
        null, RepositoryId.generate(), "PR-42"));
    assertThrows(DomainException.class, () -> new GetChangeBySourceQuery(
        TenantId.generate(), null, "PR-42"));
    assertThrows(DomainException.class, () -> new GetChangeBySourceQuery(
        TenantId.generate(), RepositoryId.generate(), "   "));
  }

  @Test
  void shouldConstructCreateOrganizationCommand() {
    CreateOrganizationCommand command = new CreateOrganizationCommand(
        "Acme Corp", actor, key);

    assertEquals("Acme Corp", command.name());
    assertEquals(actor, command.actor());
    assertEquals(key, command.idempotencyKey());
  }

  @Test
  void shouldTrimNameInCreateOrganizationCommand() {
    CreateOrganizationCommand command = new CreateOrganizationCommand(
        "  Acme Corp  ", actor, key);

    assertEquals("Acme Corp", command.name());
  }

  @Test
  void shouldRejectInvalidCreateOrganizationCommand() {
    assertThrows(DomainException.class, () -> new CreateOrganizationCommand(
        null, actor, key));
    assertThrows(DomainException.class, () -> new CreateOrganizationCommand(
        "   ", actor, key));
    assertThrows(DomainException.class, () -> new CreateOrganizationCommand(
        "Acme Corp", null, key));
    assertThrows(DomainException.class, () -> new CreateOrganizationCommand(
        "Acme Corp", actor, null));
  }

  @Test
  void shouldConstructCreateRepositoryCommand() {
    TenantId tenantId = TenantId.generate();
    CreateRepositoryCommand command = new CreateRepositoryCommand(
        tenantId, Repository.ProviderType.GITHUB, "ext-123",
        "core-backend", "https://github.com/acme/core-backend", "main",
        actor, key);

    assertEquals(tenantId, command.tenantId());
    assertEquals(Repository.ProviderType.GITHUB, command.providerType());
    assertEquals("ext-123", command.externalId());
    assertEquals("core-backend", command.name());
    assertEquals("https://github.com/acme/core-backend", command.url());
    assertEquals("main", command.defaultBranch());
    assertEquals(actor, command.actor());
    assertEquals(key, command.idempotencyKey());
  }

  @Test
  void shouldTrimStringFieldsInCreateRepositoryCommand() {
    CreateRepositoryCommand command = new CreateRepositoryCommand(
        TenantId.generate(), Repository.ProviderType.GITHUB, "  ext-123  ",
        "  core-backend  ", "  https://github.com/acme/core-backend  ", "  main  ",
        actor, key);

    assertEquals("ext-123", command.externalId());
    assertEquals("core-backend", command.name());
    assertEquals("https://github.com/acme/core-backend", command.url());
    assertEquals("main", command.defaultBranch());
  }

  @Test
  void shouldRejectInvalidCreateRepositoryCommand() {
    TenantId tenantId = TenantId.generate();
    Repository.ProviderType provider = Repository.ProviderType.GITHUB;
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        null, provider, "ext-1", "name", "url", "main", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, null, "ext-1", "name", "url", "main", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "  ", "name", "url", "main", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "ext-1", "  ", "url", "main", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "ext-1", "name", "  ", "main", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "ext-1", "name", "url", "  ", actor, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "ext-1", "name", "url", "main", null, key));
    assertThrows(DomainException.class, () -> new CreateRepositoryCommand(
        tenantId, provider, "ext-1", "name", "url", "main", actor, null));
  }

  @Test
  void shouldConstructCreateServiceCommand() {
    TenantId tenantId = TenantId.generate();
    CreateServiceCommand command = new CreateServiceCommand(
        tenantId, "payment-service", CriticalityTier.TIER_0, "team-payments", actor, key);

    assertEquals(tenantId, command.tenantId());
    assertEquals("payment-service", command.name());
    assertEquals(CriticalityTier.TIER_0, command.criticalityTier());
    assertEquals("team-payments", command.owner());
    assertEquals(actor, command.actor());
    assertEquals(key, command.idempotencyKey());
  }

  @Test
  void shouldTrimStringFieldsInCreateServiceCommand() {
    CreateServiceCommand command = new CreateServiceCommand(
        TenantId.generate(),
        "  payment-service  ",
        CriticalityTier.TIER_0,
        "  team-payments  ",
        actor, key);

    assertEquals("payment-service", command.name());
    assertEquals("team-payments", command.owner());
  }

  @Test
  void shouldRejectInvalidCreateServiceCommand() {
    TenantId tenantId = TenantId.generate();
    // null tenantId
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        null, "name", CriticalityTier.TIER_0, "owner", actor, key));
    // blank name
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "   ", CriticalityTier.TIER_0, "owner", actor, key));
    // null criticalityTier
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", null, "owner", actor, key));
    // null actor
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", CriticalityTier.TIER_0, "owner", null, key));
    // null idempotencyKey
    assertThrows(DomainException.class, () -> new CreateServiceCommand(
        tenantId, "name", CriticalityTier.TIER_0, "owner", actor, null));
    // null owner is NOT rejected (per Service domain contract).
    // Verifying the non-rejection so the contract test pins the behavior.
    CreateServiceCommand withNullOwner = new CreateServiceCommand(
        tenantId, "name", CriticalityTier.TIER_0, null, actor, key);
    assertNull(withNullOwner.owner());
  }

  @Test
  void shouldConstructCreatePolicyCommand() {
    TenantId tenantId = TenantId.generate();
    PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
    CreatePolicyCommand command = new CreatePolicyCommand(
        tenantId, "core-policy", "description", PolicyVersion.of("v1"),
        List.of(rule), actor, key);

    assertEquals(tenantId, command.tenantId());
    assertEquals("core-policy", command.name());
    assertEquals("description", command.description());
    assertEquals("v1", command.version().value());
    assertEquals(1, command.rules().size());
    assertEquals(rule, command.rules().get(0));
    assertEquals(actor, command.actor());
    assertEquals(key, command.idempotencyKey());
  }

  @Test
  void shouldTrimStringFieldsInCreatePolicyCommand() {
    PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
    CreatePolicyCommand command = new CreatePolicyCommand(
        TenantId.generate(), "  core-policy  ", "  description  ",
        PolicyVersion.of("v1"), List.of(rule), actor, key);

    assertEquals("core-policy", command.name());
    assertEquals("description", command.description());
  }

  @Test
  void shouldRejectInvalidCreatePolicyCommand() {
    TenantId tenantId = TenantId.generate();
    PolicyRule rule = new PolicyRule("R1", Set.of(), Set.of(), Set.of(),
        DecisionOutcome.APPROVE, List.of(), "Explanation");
    // null tenantId
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        null, "name", "", PolicyVersion.of("v1"), List.of(rule), actor, key));
    // blank name
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "   ", "", PolicyVersion.of("v1"), List.of(rule), actor, key));
    // null version
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "", null, List.of(rule), actor, key));
    // empty rules
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "", PolicyVersion.of("v1"), List.of(), actor, key));
    // null actor
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "", PolicyVersion.of("v1"), List.of(rule), null, key));
    // null idempotencyKey
    assertThrows(DomainException.class, () -> new CreatePolicyCommand(
        tenantId, "name", "", PolicyVersion.of("v1"), List.of(rule), actor, null));
  }

  @Test
  void shouldConstructSearchEvidenceQuery() {
    SearchEvidenceQuery query = new SearchEvidenceQuery("kafka", Map.of(), 25);

    assertEquals("kafka", query.query());
    assertEquals(25, query.limit());
    assertTrue(query.filters().isEmpty());
  }

  @Test
  void shouldDefaultSearchEvidenceQueryLimit() {
    SearchEvidenceQuery query = new SearchEvidenceQuery("kafka", Map.of());

    assertEquals(SearchEvidenceQuery.DEFAULT_LIMIT, query.limit());
    assertEquals(20, query.limit());
  }

  @Test
  void shouldTrimSearchEvidenceQuery() {
    SearchEvidenceQuery query = new SearchEvidenceQuery("  kafka   ", Map.of());

    assertEquals("kafka", query.query());
  }

  @Test
  void shouldRejectInvalidSearchEvidenceQuery() {
    // null/blank query
    assertThrows(DomainException.class, () -> new SearchEvidenceQuery(null, Map.of()));
    assertThrows(DomainException.class, () -> new SearchEvidenceQuery("   ", Map.of()));
    // null filters
    assertThrows(DomainException.class, () -> new SearchEvidenceQuery("kafka", null));
    // non-empty filters are structurally reserved (D1) and rejected
    assertThrows(DomainException.class,
        () -> new SearchEvidenceQuery("kafka", Map.of("source", "INCIDENT")));
    // limit out of range
    assertThrows(DomainException.class, () -> new SearchEvidenceQuery("kafka", Map.of(), 0));
    assertThrows(DomainException.class, () -> new SearchEvidenceQuery("kafka", Map.of(), -5));
  }

  @Test
  void shouldConstructCreatePolicyCommandWithFullRule() {
    TenantId tenantId = TenantId.generate();
    PolicyRule rule = new PolicyRule(
        "R1",
        Set.of(CriticalityTier.TIER_0, CriticalityTier.TIER_1),
        Set.of(RiskLevel.HIGH, RiskLevel.CRITICAL),
        Set.of(EvidenceState.EVIDENCE_AVAILABLE),
        DecisionOutcome.REVIEW_REQUIRED,
        List.of(RequiredAction.HUMAN_REVIEW, RequiredAction.SECURITY_REVIEW),
        "Requires human review");
    CreatePolicyCommand command = new CreatePolicyCommand(
        tenantId, "core-policy", "", PolicyVersion.of("v2"),
        List.of(rule), actor, key);

    PolicyRule stored = command.rules().get(0);
    assertEquals("R1", stored.ruleId());
    assertEquals(Set.of(CriticalityTier.TIER_0, CriticalityTier.TIER_1), stored.targetTiers());
    assertEquals(Set.of(RiskLevel.HIGH, RiskLevel.CRITICAL), stored.targetRiskLevels());
    assertEquals(Set.of(EvidenceState.EVIDENCE_AVAILABLE), stored.requiredEvidenceStates());
    assertEquals(DecisionOutcome.REVIEW_REQUIRED, stored.outcome());
    assertEquals(List.of(RequiredAction.HUMAN_REVIEW, RequiredAction.SECURITY_REVIEW), stored.actions());
    assertEquals("Requires human review", stored.explanation());
  }

  @Test
  void shouldConstructOverrideDecisionCommand() {
    TenantId tenantId = TenantId.generate();
    Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
    AnalysisRunId runId = AnalysisRunId.generate();
    DecisionId decisionId = DecisionId.generate();
    OverrideDecisionCommand command = new OverrideDecisionCommand(
        tenantId, admin, runId, decisionId, DecisionOutcome.BLOCK, "Security incident");

    assertEquals(tenantId, command.tenantId());
    assertEquals(admin, command.actor());
    assertEquals(runId, command.analysisRunId());
    assertEquals(decisionId, command.decisionId());
    assertEquals(DecisionOutcome.BLOCK, command.newOutcome());
    assertEquals("Security incident", command.justification());
  }

  @Test
  void shouldTrimJustificationInOverrideDecisionCommand() {
    OverrideDecisionCommand command = new OverrideDecisionCommand(
        TenantId.generate(), new Actor("admin-1", Actor.Role.TENANT_ADMIN),
        AnalysisRunId.generate(), DecisionId.generate(),
        DecisionOutcome.BLOCK, "  justified override  ");

    assertEquals("justified override", command.justification());
  }

  @Test
  void shouldRejectInvalidOverrideDecisionCommand() {
    Actor admin = new Actor("admin-1", Actor.Role.TENANT_ADMIN);
    AnalysisRunId runId = AnalysisRunId.generate();
    DecisionId decisionId = DecisionId.generate();
    // null tenantId
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        null, admin, runId, decisionId, DecisionOutcome.BLOCK, "why"));
    // null actor
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        TenantId.generate(), null, runId, decisionId, DecisionOutcome.BLOCK, "why"));
    // null analysisRunId
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        TenantId.generate(), admin, null, decisionId, DecisionOutcome.BLOCK, "why"));
    // null decisionId
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        TenantId.generate(), admin, runId, null, DecisionOutcome.BLOCK, "why"));
    // null outcome
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        TenantId.generate(), admin, runId, decisionId, null, "why"));
    // blank justification (override must never be silent)
    assertThrows(DomainException.class, () -> new OverrideDecisionCommand(
        TenantId.generate(), admin, runId, decisionId, DecisionOutcome.BLOCK, "   "));
  }
}