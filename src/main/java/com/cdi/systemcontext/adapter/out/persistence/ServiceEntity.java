package com.cdi.systemcontext.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code service} table (data-model.md §3.A, V8
 * migration).
 *
 * <p>Persistence-only mapping of the {@code Service} aggregate: identifiers
 * and scalar state only, no domain logic, no relationships. Status is stored
 * as the enum name. Column names follow the Spring camelCase-to-snake_case
 * strategy and must exactly match the V8 columns (ddl-auto: validate).
 *
 * <p>The {@code tenant_id} column is the tenant scope of the service row
 * (data-model.md §2): the Service aggregate carries the {@code TenantId}
 * directly, so it is stored alongside the row and used to scope the natural-key
 * lookup. The {@code updated_at} column is initialized to {@code created_at} on
 * insert by the mapper; the domain Service currently has no field exposing
 * {@code updatedAt} (mirrors the {@code Organization} precedent — the entity
 * carries {@code updated_at} for {@code data-model.md §6} audit-field
 * compliance and ddl-auto validation).
 *
 * <p>The {@code owner} column is nullable, mirroring the {@code Service}
 * domain constructor which accepts a null owner.
 */
@Entity
@Table(name = "service")
public class ServiceEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private String name;

  private String criticalityTier;

  private String owner;

  private String status;

  private Instant createdAt;

  private Instant updatedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public String getName() { return name; }

  public void setName(String name) { this.name = name; }

  public String getCriticalityTier() { return criticalityTier; }

  public void setCriticalityTier(String criticalityTier) { this.criticalityTier = criticalityTier; }

  public String getOwner() { return owner; }

  public void setOwner(String owner) { this.owner = owner; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
