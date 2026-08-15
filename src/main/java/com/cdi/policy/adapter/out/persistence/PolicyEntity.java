package com.cdi.policy.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA representation of the {@code policy} table (data-model.md §E, V9
 * migration).
 *
 * <p>Persistence-only mapping of the {@code Policy} aggregate: identifiers and
 * scalar immutable state only, no domain logic. The 1:N {@code PolicyRule}
 * children are owned in the {@code policy_rule} table (data-model.md §7
 * 1:N-child precedent) and saved/loaded together with the parent via cascade —
 * mirroring {@code RiskAssessmentEntity}/{@code RiskFactorEntity}. Status is
 * stored as the enum name. Column names follow the Spring
 * camelCase-to-snake_case strategy and must exactly match the V9 columns
 * (ddl-auto: validate).
 *
 * <p>The {@code tenant_id} column is the tenant scope of the policy row
 * (data-model.md §2): the {@code Policy} aggregate carries the {@code TenantId}
 * directly, so it is stored alongside the row and used to scope the
 * {@code findActiveByTenant} lookup. The {@code updated_at} column is
 * initialized to {@code created_at} on insert by the mapper; the domain Policy
 * has no field exposing {@code updatedAt} (mirrors the {@code Organization}
 * and {@code Service} precedent — the entity carries {@code updated_at} for
 * {@code data-model.md §6} audit-field compliance and ddl-auto validation).
 */
@Entity
@Table(name = "policy")
public class PolicyEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private String name;

  private String description;

  private String status;

  private String version;

  private Instant createdAt;

  private Instant updatedAt;

  @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL,
      orphanRemoval = true, fetch = FetchType.EAGER)
  private List<PolicyRuleEntity> rules = new ArrayList<>();

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public String getName() { return name; }

  public void setName(String name) { this.name = name; }

  public String getDescription() { return description; }

  public void setDescription(String description) { this.description = description; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public String getVersion() { return version; }

  public void setVersion(String version) { this.version = version; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public List<PolicyRuleEntity> getRules() { return rules; }

  public void setRules(List<PolicyRuleEntity> rules) { this.rules = rules; }
}
