package com.cdi.organization.adapter.out.persistence;

import com.cdi.common.domain.id.TenantId;
import com.cdi.organization.domain.Organization;

/**
 * Maps between the {@code Organization} aggregate and its persistence
 * representation.
 *
 * <p>Unlike other aggregates (e.g., {@code Change}) where the tenant scope
 * lives on the port call and is stored alongside the row, the Organization
 * <em>is</em> the tenant root: its {@code id} is the tenant identity. No
 * separate {@code tenantId} parameter is needed on the conversion.
 *
 * <p>The {@code updated_at} column is initialized to {@code createdAt} on
 * write; the domain Organization does not expose {@code updatedAt}, so on
 * read the entity's value is ignored (the domain is rebuilt via
 * {@link Organization#restore}).
 */
final class OrganizationMapper {

  private OrganizationMapper() {
    // utility class
  }

  static OrganizationEntity toEntity(Organization organization) {
    OrganizationEntity entity = new OrganizationEntity();
    entity.setId(organization.getId().value());
    entity.setName(organization.getName());
    entity.setStatus(organization.getStatus().name());
    entity.setCreatedAt(organization.getCreatedAt());
    entity.setUpdatedAt(organization.getCreatedAt());
    return entity;
  }

  static Organization toDomain(OrganizationEntity entity) {
    return Organization.restore(
        new TenantId(entity.getId()),
        entity.getName(),
        Organization.Status.valueOf(entity.getStatus()),
        entity.getCreatedAt());
  }
}
