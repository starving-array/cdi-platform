package com.cdi.systemcontext.adapter.out.persistence;

import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.Service;

/**
 * Maps between the {@code Service} aggregate and its persistence
 * representation.
 *
 * <p>The {@code Service} aggregate carries its own {@code TenantId}
 * directly (it is a tenant-scoped child aggregate), so the conversion needs no
 * separate {@code tenantId} argument — the tenant scope is read/written from
 * the aggregate itself.
 *
 * <p>The {@code updated_at} column is initialized to {@code createdAt} on
 * write; the domain Service exposes no {@code updatedAt} field (it has only
 * {@code createdAt}). On read the entity's {@code updatedAt} is not surfaced
 * to the domain — the aggregate is rebuilt via {@link Service#restore}.
 *
 * <p>The nullable {@code owner} field is preserved verbatim in both
 * directions; no trimming or normalization happens during hydration (the
 * aggregate's constructor already trims on the create path).
 */
final class ServiceMapper {

  private ServiceMapper() {
    // utility class
  }

  static ServiceEntity toEntity(Service service) {
    ServiceEntity entity = new ServiceEntity();
    entity.setId(service.getId().value());
    entity.setTenantId(service.getTenantId().value());
    entity.setName(service.getName());
    entity.setCriticalityTier(service.getCriticality().name());
    entity.setOwner(service.getOwner());
    entity.setStatus(service.getStatus().name());
    entity.setCreatedAt(service.getCreatedAt());
    entity.setUpdatedAt(service.getCreatedAt());
    return entity;
  }

  static Service toDomain(ServiceEntity entity) {
    return Service.restore(
        new ServiceId(entity.getId()),
        new TenantId(entity.getTenantId()),
        entity.getName(),
        CriticalityTier.valueOf(entity.getCriticalityTier()),
        entity.getOwner(),
        Service.Status.valueOf(entity.getStatus()),
        entity.getCreatedAt());
  }
}
