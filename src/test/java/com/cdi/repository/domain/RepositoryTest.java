package com.cdi.repository.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

    @Test
    void shouldCreateValidRepository() {
        Repository repo = new Repository(
            RepositoryId.generate(), 
            TenantId.generate(), 
            Repository.ProviderType.GITHUB, 
            "ext-123", 
            "core-backend", 
            "https://github.com/acme/core-backend", 
            "main", 
            Instant.now()
        );
        
        assertEquals(Repository.Status.ACTIVE, repo.getStatus());
        assertEquals(Repository.ProviderType.GITHUB, repo.getProviderType());
    }

    @Test
    void shouldRejectInvalidFields() {
        assertThrows(DomainException.class, () -> 
            new Repository(RepositoryId.generate(), TenantId.generate(), null, "123", "name", "url", "main", Instant.now())
        );
        assertThrows(DomainException.class, () -> 
            new Repository(RepositoryId.generate(), TenantId.generate(), Repository.ProviderType.GITLAB, "  ", "name", "url", "main", Instant.now())
        );
    }

    @Test
    void shouldArchiveSuccessfully() {
        Repository repo = new Repository(RepositoryId.generate(), TenantId.generate(), Repository.ProviderType.GITHUB, "123", "name", "url", "main", Instant.now());
        repo.archive();
        assertEquals(Repository.Status.ARCHIVED, repo.getStatus());
        assertThrows(DomainException.class, repo::archive);
    }

    @Test
    void restoreRebuildsActiveRepositoryExactlyAsPersisted() {
        RepositoryId id = RepositoryId.generate();
        TenantId tenantId = TenantId.generate();
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-15T10:30:00Z");

        Repository restored = Repository.restore(
            id, tenantId, Repository.ProviderType.GITHUB, "ext-42",
            "core-backend", "https://github.com/acme/core-backend", "main",
            Repository.Status.ACTIVE, createdAt, updatedAt);

        assertEquals(id, restored.getId());
        assertEquals(tenantId, restored.getTenantId());
        assertEquals(Repository.ProviderType.GITHUB, restored.getProviderType());
        assertEquals("ext-42", restored.getExternalId());
        assertEquals("core-backend", restored.getName());
        assertEquals("https://github.com/acme/core-backend", restored.getUrl());
        assertEquals("main", restored.getDefaultBranch());
        assertEquals(Repository.Status.ACTIVE, restored.getStatus());
        assertEquals(createdAt, restored.getCreatedAt());
        assertEquals(updatedAt, restored.getUpdatedAt());
    }

    @Test
    void restoreRebuildsArchivedRepositoryWithoutInvokingArchiveTransition() {
        RepositoryId id = RepositoryId.generate();
        TenantId tenantId = TenantId.generate();
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-15T11:00:00Z");

        Repository restored = Repository.restore(
            id, tenantId, Repository.ProviderType.GITLAB, "ext-99",
            "legacy-service", "https://gitlab.com/acme/legacy-service", "develop",
            Repository.Status.ARCHIVED, createdAt, updatedAt);

        assertEquals(Repository.Status.ARCHIVED, restored.getStatus());
        // restore is a hydration factory; it must not throw even though archive()
        // would normally reject an already-archived repository. The reconstructed
        // aggregate is exactly as persisted.
        assertEquals(id, restored.getId());
        assertEquals(Repository.ProviderType.GITLAB, restored.getProviderType());
        assertEquals(updatedAt, restored.getUpdatedAt());
    }

    @Test
    void newRepositoryInitializesUpdatedAtToCreatedAt() {
        Instant createdAt = Instant.parse("2026-08-15T10:00:00Z");

        Repository repo = new Repository(
            RepositoryId.generate(), TenantId.generate(), Repository.ProviderType.GITHUB,
            "123", "name", "url", "main", createdAt);

        assertEquals(createdAt, repo.getUpdatedAt());
    }
}
