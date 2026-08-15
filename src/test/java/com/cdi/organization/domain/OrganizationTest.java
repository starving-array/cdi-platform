package com.cdi.organization.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationTest {

    @Test
    void shouldCreateValidOrganization() {
        TenantId tenantId = TenantId.generate();
        Organization org = new Organization(tenantId, "Acme Corp", Instant.now());
        
        assertEquals(tenantId, org.getId());
        assertEquals("Acme Corp", org.getName());
        assertEquals(Organization.Status.ACTIVE, org.getStatus());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(DomainException.class, () -> 
            new Organization(TenantId.generate(), "   ", Instant.now())
        );
    }

    @Test
    void shouldSuspendAndReactivate() {
        Organization org = new Organization(TenantId.generate(), "Acme Corp", Instant.now());
        
        org.suspend();
        assertEquals(Organization.Status.SUSPENDED, org.getStatus());
        
        assertThrows(DomainException.class, org::suspend);
        
        org.reactivate();
        assertEquals(Organization.Status.ACTIVE, org.getStatus());
    }
}
