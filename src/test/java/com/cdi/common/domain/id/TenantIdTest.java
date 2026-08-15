package com.cdi.common.domain.id;

import com.cdi.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantIdTest {

    @Test
    void shouldCreateTenantIdFromValidUuid() {
        UUID uuid = UUID.randomUUID();
        TenantId id = new TenantId(uuid);
        assertEquals(uuid, id.value());
    }

    @Test
    void shouldThrowExceptionWhenNull() {
        DomainException exception = assertThrows(DomainException.class, () -> new TenantId(null));
        assertEquals("TenantId value cannot be null", exception.getMessage());
    }

    @Test
    void shouldGenerateRandomTenantId() {
        TenantId id1 = TenantId.generate();
        TenantId id2 = TenantId.generate();
        assertNotNull(id1.value());
        assertNotEquals(id1, id2);
    }

    @Test
    void shouldCreateFromString() {
        String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
        TenantId id = TenantId.fromString(uuidStr);
        assertEquals(UUID.fromString(uuidStr), id.value());
    }

    @Test
    void shouldThrowExceptionForInvalidString() {
        DomainException exception = assertThrows(DomainException.class, () -> TenantId.fromString("invalid-uuid"));
        assertTrue(exception.getMessage().contains("Invalid UUID format"));
    }

    @Test
    void shouldProvideValueEquality() {
        UUID uuid = UUID.randomUUID();
        TenantId id1 = new TenantId(uuid);
        TenantId id2 = new TenantId(uuid);
        
        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }
}
