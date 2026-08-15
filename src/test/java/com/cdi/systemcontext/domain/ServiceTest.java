package com.cdi.systemcontext.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {

    @Test
    void shouldCreateValidService() {
        Service svc = new Service(ServiceId.generate(), TenantId.generate(), "Payment Service", CriticalityTier.TIER_0, "Team Alpha", Instant.now());
        assertEquals("Payment Service", svc.getName());
        assertEquals(CriticalityTier.TIER_0, svc.getCriticality());
        assertEquals(Service.Status.ACTIVE, svc.getStatus());
        assertTrue(svc.getDependencies().isEmpty());
    }

    @Test
    void shouldAddValidDependency() {
        TenantId tenantId = TenantId.generate();
        Service source = new Service(ServiceId.generate(), tenantId, "Source", CriticalityTier.TIER_1, null, Instant.now());
        Service target = new Service(ServiceId.generate(), tenantId, "Target", CriticalityTier.TIER_2, null, Instant.now());
        
        source.addDependency(target);
        
        assertEquals(1, source.getDependencies().size());
        assertTrue(source.getDependencies().contains(new ServiceDependency(target.getId())));
    }

    @Test
    void shouldRejectCrossTenantDependency() {
        Service source = new Service(ServiceId.generate(), TenantId.generate(), "Source", CriticalityTier.TIER_1, null, Instant.now());
        Service target = new Service(ServiceId.generate(), TenantId.generate(), "Target", CriticalityTier.TIER_2, null, Instant.now());
        
        DomainException ex = assertThrows(DomainException.class, () -> source.addDependency(target));
        assertEquals("Cannot depend on a service from a different organization", ex.getMessage());
    }

    @Test
    void shouldRejectSelfDependency() {
        Service source = new Service(ServiceId.generate(), TenantId.generate(), "Source", CriticalityTier.TIER_1, null, Instant.now());
        
        DomainException ex = assertThrows(DomainException.class, () -> source.addDependency(source));
        assertEquals("A service cannot depend on itself", ex.getMessage());
    }

    @Test
    void shouldDeprecateService() {
        Service svc = new Service(ServiceId.generate(), TenantId.generate(), "Legacy", CriticalityTier.TIER_3, null, Instant.now());
        svc.deprecate();
        assertEquals(Service.Status.DEPRECATED, svc.getStatus());
        assertThrows(DomainException.class, svc::deprecate);
    }

    @Test
    void restoreRebuildsActiveServiceExactlyAsPersisted() {
        ServiceId id = ServiceId.generate();
        TenantId tenantId = TenantId.generate();
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");

        Service restored = Service.restore(
            id, tenantId, "payment-service", CriticalityTier.TIER_0, "team-payments",
            Service.Status.ACTIVE, createdAt);

        assertEquals(id, restored.getId());
        assertEquals(tenantId, restored.getTenantId());
        assertEquals("payment-service", restored.getName());
        assertEquals(CriticalityTier.TIER_0, restored.getCriticality());
        assertEquals("team-payments", restored.getOwner());
        assertEquals(Service.Status.ACTIVE, restored.getStatus());
        assertEquals(createdAt, restored.getCreatedAt());
        assertTrue(restored.getDependencies().isEmpty());
    }

    @Test
    void restoreRebuildsDeprecatedServiceWithoutInvokingTransition() {
        ServiceId id = ServiceId.generate();
        TenantId tenantId = TenantId.generate();
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");

        Service restored = Service.restore(
            id, tenantId, "legacy-service", CriticalityTier.TIER_2, "team-legacy",
            Service.Status.DEPRECATED, createdAt);

        // restore is a hydration factory; it must not throw even though deprecate()
        // would normally reject an already-deprecated service. The reconstructed
        // aggregate is exactly as persisted.
        assertEquals(Service.Status.DEPRECATED, restored.getStatus());
        assertEquals(id, restored.getId());
        assertEquals("legacy-service", restored.getName());
        assertEquals(CriticalityTier.TIER_2, restored.getCriticality());
        assertEquals("team-legacy", restored.getOwner());
        assertEquals(createdAt, restored.getCreatedAt());
    }

    @Test
    void restorePreservesNullableOwner() {
        Service restored = Service.restore(
            ServiceId.generate(), TenantId.generate(), "orphan-service", CriticalityTier.TIER_3,
            null, Service.Status.ACTIVE, Instant.parse("2026-08-15T10:00:00Z"));

        assertNull(restored.getOwner());
    }

    @Test
    void restorePreservesNonNullOwner() {
        Service restored = Service.restore(
            ServiceId.generate(), TenantId.generate(), "owned-service", CriticalityTier.TIER_1,
            "team-revenue", Service.Status.ACTIVE, Instant.parse("2026-08-15T10:00:00Z"));

        assertEquals("team-revenue", restored.getOwner());
    }

    @Test
    void restorePreservesConstructorInvariants() {
        ServiceId id = ServiceId.generate();
        TenantId tenantId = TenantId.generate();

        Service restored = Service.restore(
            id, tenantId, "  trimmed-name  ", CriticalityTier.TIER_0, "  team-alpha  ",
            Service.Status.ACTIVE, Instant.parse("2026-08-15T10:00:00Z"));

        // restore delegates to the validated constructor, so constructor
        // invariants (trimming, ACTIVE default, empty dependencies, no repo)
        // are preserved before the persisted status is assigned.
        assertEquals("trimmed-name", restored.getName());
        assertEquals("team-alpha", restored.getOwner());
        assertTrue(restored.getDependencies().isEmpty());
        assertNull(restored.getRepositoryId());
    }
}
