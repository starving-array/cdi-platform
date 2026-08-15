package com.cdi.repository.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code repository} table (data-model.md §3, V7
 * migration).
 *
 * <p>Persistence-only mapping of the {@code Repository} aggregate: identifiers
 * and scalar state only, no domain logic, no relationships. Status is stored
 * as the enum name. Column names follow the Spring camelCase-to-snake_case
 * strategy and must exactly match the V7 columns (ddl-auto: validate).
 *
 * <p>The {@code tenant_id} column is the tenant scope of the repository row
 * (data-model.md §2): the Repository aggregate carries the {@code TenantId}
 * directly, so it is stored alongside the row and used to scope the natural-key
 * lookup. The {@code updated_at} column is initialized to {@code created_at} on
 * insert by the mapper; the domain Repository currently has no
 * status-transition that moves {@code updatedAt} past creation
 * ({@code archive()} is status-only and untouched by UC-08).
 */
@Entity
@Table(name = "repository")
public class RepositoryEntity {

  @Id
  private UUID id;

  private UUID tenantId;

  private String providerType;

  private String externalId;

  private String name;

  private String url;

  private String defaultBranch;

  private String status;

  private Instant createdAt;

  private Instant updatedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public UUID getTenantId() { return tenantId; }

  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

  public String getProviderType() { return providerType; }

  public void setProviderType(String providerType) { this.providerType = providerType; }

  public String getExternalId() { return externalId; }

  public void setExternalId(String externalId) { this.externalId = externalId; }

  public String getName() { return name; }

  public void setName(String name) { this.name = name; }

  public String getUrl() { return url; }

  public void setUrl(String url) { this.url = url; }

  public String getDefaultBranch() { return defaultBranch; }

  public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
