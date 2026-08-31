package com.cdi.common.adapter.out.github;

import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.repository.domain.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@RestClientTest(GithubSourceControlAdapter.class)
@TestPropertySource(properties = {"cdi.github.token=test-token"})
class GithubSourceControlAdapterTest {

    @Autowired
    private GithubSourceControlAdapter adapter;

    @Autowired
    private MockRestServiceServer mockServer;

    @MockBean
    private RepositoryRepository repositoryRepository;

    private TenantId tenantId;
    private RepositoryId repoId;
    private Repository repository;
    private Repository gitlabRepository;

    @BeforeEach
    void setUp() {
        tenantId = TenantId.generate();
        repoId = RepositoryId.generate();
        repository = new Repository(repoId, tenantId, Repository.ProviderType.GITHUB, "12345", "test-repo", "url", "main", Instant.now());
        gitlabRepository = new Repository(RepositoryId.generate(), tenantId, Repository.ProviderType.GITLAB, "67890", "gl-repo", "url", "main", Instant.now());

        when(repositoryRepository.findByTenantIdAndId(tenantId, repoId))
                .thenReturn(Optional.of(repository));
        when(repositoryRepository.findByTenantIdAndId(tenantId, gitlabRepository.getId()))
                .thenReturn(Optional.of(gitlabRepository));
    }

    @Test
    void getChangeMetadata_success() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andExpect(MockRestRequestMatchers.header("Authorization", "Bearer test-token"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/pulls/42"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "title": "Fix bug",
                          "body": "Fixed it",
                          "user": {"login": "alice"},
                          "head": {"ref": "feature", "sha": "abc1234"},
                          "base": {"ref": "main"}
                        }
                        """, MediaType.APPLICATION_JSON));

        ChangeMetadata meta = adapter.getChangeMetadata(tenantId, repoId, "42");
        assertEquals("42", meta.providerChangeId());
        assertEquals("Fix bug", meta.title());
        assertEquals("Fixed it", meta.description());
        assertEquals("alice", meta.author());
        assertEquals("feature", meta.sourceBranch());
        assertEquals("main", meta.targetBranch());
        assertEquals("abc1234", meta.latestCommitSha());
    }

    @Test
    void getDiff_success_parsing() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/commits/abc1234"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "files": [
                            {"filename": "a.txt", "additions": 10, "deletions": 2, "status": "added"},
                            {"filename": "b.txt", "additions": 1, "deletions": 5, "status": "removed"},
                            {"filename": "c.txt", "additions": 3, "deletions": 3, "status": "modified"},
                            {"filename": "d.txt", "additions": 5, "deletions": 0, "status": "copied"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FileDiff> diffs = adapter.getDiff(tenantId, repoId, "abc1234");
        assertEquals(4, diffs.size());
        assertEquals(FileDiff.ChangeType.ADDED, diffs.get(0).changeType());
        assertEquals(FileDiff.ChangeType.DELETED, diffs.get(1).changeType());
        assertEquals(FileDiff.ChangeType.MODIFIED, diffs.get(2).changeType());
        assertEquals(FileDiff.ChangeType.ADDED, diffs.get(3).changeType());
    }

    @Test
    void getDiff_emptyFiles() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/commits/abc1234"))
                .andRespond(MockRestResponseCreators.withSuccess("{}", MediaType.APPLICATION_JSON));

        List<FileDiff> diffs = adapter.getDiff(tenantId, repoId, "abc1234");
        assertTrue(diffs.isEmpty());
    }

    @Test
    void providerMismatch_throwsException() {
        DomainException ex = assertThrows(DomainException.class, () -> adapter.getChangeMetadata(tenantId, gitlabRepository.getId(), "42"));
        assertTrue(ex.getMessage().contains("Unsupported provider"));
    }

    @Test
    void publishStatusCheck_mapsOutcomesCorrectly() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/statuses/abc1234"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.jsonPath("$.state").value("success"))
                .andRespond(MockRestResponseCreators.withSuccess());

        adapter.publishStatusCheck(tenantId, repoId, "abc1234", DecisionOutcome.APPROVE, List.of(), "http://url");
    }

    @Test
    void github404_throwsException() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND));

        DomainException ex = assertThrows(DomainException.class, () -> adapter.getChangeMetadata(tenantId, repoId, "42"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void github429_throwsException() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/pulls/42"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.TOO_MANY_REQUESTS));

        DomainException ex = assertThrows(DomainException.class, () -> adapter.getChangeMetadata(tenantId, repoId, "42"));
        assertTrue(ex.getMessage().contains("Failed to fetch"));
    }
}
