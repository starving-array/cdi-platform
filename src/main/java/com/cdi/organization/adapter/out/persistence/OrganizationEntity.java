package com.cdi.organization.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of the {@code tenant} table (data-model.md §3.A).
 *
 * <p>Persistence-only mapping of the {@code Organization} aggregate:
 * identifiers and scalar state only, no domain logic, no relationships. The
 * {@code id} column is the organization's own id which doubles as the tenant
 * identity (organization-repository-service-domain.md). Status is stored as
 * the enum name. Column names follow the Spring camelCase-to-snake_case
 * strategy.
 *
 * <p>The {@code updated_at} column is initialized to {@code created_at} on
 * insert. The domain Organization currently exposes no {@code updatedAt}
 * field; a future use case that exercises state transitions (suspend /
 * reactivate) through the application layer can promote it into the domain.
 */
@Entity
@Table(name = "tenant")
public class OrganizationEntity {

  @Id
  private UUID id;

  private String name;

  private String status;

  private Instant createdAt;

  private Instant updatedAt;

  public UUID getId() { return id; }

  public void setId(UUID id) { this.id = id; }

  public String getName() { return name; }

  public void setName(String name) { this.name = name; }

  public String getStatus() { return status; }

  public void setStatus(String status) { this.status = status; }

  public Instant getCreatedAt() { return createdAt; }

  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
