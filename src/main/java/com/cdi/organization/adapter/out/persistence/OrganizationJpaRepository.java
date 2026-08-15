package com.cdi.organization.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository over the {@code tenant} table.
 *
 * <p>Derived findBy query backing the {@code OrganizationRepository}
 * natural-key (name) lookup used by UC-07 duplicate detection
 * (application-layer.md §10). The {@code UNIQUE(name)} DB constraint
 * (data-model.md §5) is the race-safe final guarantee.
 */
public interface OrganizationJpaRepository extends JpaRepository<OrganizationEntity, UUID> {

  Optional<OrganizationEntity> findByName(String name);

  long countByName(String name);
}
