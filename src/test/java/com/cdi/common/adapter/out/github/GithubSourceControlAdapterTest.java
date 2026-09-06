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
    void getChangeMetadata_withOwnerRepoFormat_skipsRepositoryLookup() {
        RepositoryId customRepoId = RepositoryId.generate();
        Repository customRepo = new Repository(customRepoId, tenantId, Repository.ProviderType.GITHUB, "starving-array/demo-test", "demo-test", "url", "main", Instant.now());
        when(repositoryRepository.findByTenantIdAndId(tenantId, customRepoId))
                .thenReturn(Optional.of(customRepo));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/starving-array/demo-test/pulls/42"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "title": "Fix bug",
                          "body": "Fixed it",
                          "user": {"login": "alice"},
                          "head": {"ref": "feature", "sha": "abc1234"},
                          "base": {"ref": "main"}
                        }
                        """, MediaType.APPLICATION_JSON));

        ChangeMetadata meta = adapter.getChangeMetadata(tenantId, customRepoId, "42");
        assertEquals("42", meta.providerChangeId());
        assertEquals("Fix bug", meta.title());
        mockServer.verify();
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
    void getDiff_preservesPatchContent() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/commits/abc1234"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "files": [
                            {"filename": "a.java", "additions": 10, "deletions": 2, "status": "modified", "patch": "@@ -1,3 +1,3 @@\\n-old\\n+new"},
                            {"filename": "b.java", "additions": 5, "deletions": 0, "status": "added", "patch": "@@ -0,0 +1,5 @@\\n+content"},
                            {"filename": "c.java", "additions": 0, "deletions": 8, "status": "removed", "patch": "@@ -1,8 +0,0 @@\\n-gone"},
                            {"filename": "d.java", "additions": 1, "deletions": 1, "status": "renamed", "patch": "@@ -1 +1 @@\\n-ren\\n+ened"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FileDiff> diffs = adapter.getDiff(tenantId, repoId, "abc1234");

        assertEquals(4, diffs.size());
        assertEquals("@@ -1,3 +1,3 @@\n-old\n+new", diffs.get(0).patch());
        assertEquals(FileDiff.ChangeType.MODIFIED, diffs.get(0).changeType());
        assertEquals("@@ -0,0 +1,5 @@\n+content", diffs.get(1).patch());
        assertEquals(FileDiff.ChangeType.ADDED, diffs.get(1).changeType());
        assertEquals("@@ -1,8 +0,0 @@\n-gone", diffs.get(2).patch());
        assertEquals(FileDiff.ChangeType.DELETED, diffs.get(2).changeType());
        assertEquals("@@ -1 +1 @@\n-ren\n+ened", diffs.get(3).patch());
        assertEquals(FileDiff.ChangeType.MODIFIED, diffs.get(3).changeType());
    }

    @Test
    void getDiff_missingPatchIsNull_notCrash() {
        // Binary files / large diffs: GitHub omits the patch field entirely.
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/commits/abc1234"))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "files": [
                            {"filename": "image.png", "additions": 0, "deletions": 0, "status": "modified"},
                            {"filename": "big.jar", "additions": 0, "deletions": 0, "status": "removed", "patch": null}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FileDiff> diffs = adapter.getDiff(tenantId, repoId, "abc1234");

        assertEquals(2, diffs.size());
        assertTrue(diffs.get(0).patchOptional().isEmpty());
        assertNull(diffs.get(1).patch());
        assertTrue(diffs.get(1).patchOptional().isEmpty());
    }

    // --- PART 3: getFileContent at the exact commit SHA ---

    @Test
    void getFileContent_retrievesAtExactSha_andDecodesBase64() {
        String javaSource = "package demo;\npublic class PaymentService {}\n";
        String base64 = java.util.Base64.getEncoder().encodeToString(javaSource.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        // Requested SHA (e6c258...) differs from the repository default-branch HEAD —
        // the adapter MUST send the requested SHA as ref, never the branch.
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/src%2Fmain%2FPaymentService.java?ref=e6c2584f24584da6017c3b89140e96aa1416b7b4")))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                          "type": "file",
                          "encoding": "base64",
                          "content": \"""" + base64 + """
                        "
                        }
                        """, MediaType.APPLICATION_JSON));

        byte[] content = adapter.getFileContent(tenantId, repoId, "src/main/PaymentService.java",
                "e6c2584f24584da6017c3b89140e96aa1416b7b4");

        assertEquals(javaSource, new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void getFileContent_emptyFile_returnsEmptyBytes() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/empty.txt?ref=abc1234")))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"type\": \"file\", \"encoding\": \"base64\", \"content\": \"\"}", MediaType.APPLICATION_JSON));

        byte[] content = adapter.getFileContent(tenantId, repoId, "empty.txt", "abc1234");
        assertEquals(0, content.length);
    }

    @Test
    void getFileContent_binaryFile_returnsRawBytes() {
        byte[] binary = new byte[] {0x00, 0x01, (byte) 0xFF, 0x10, 0x7F, (byte) 0x80};
        String base64 = java.util.Base64.getEncoder().encodeToString(binary);

        // Base64 with embedded newlines, as returned by the real Contents API.
        String multilineB64 = base64.substring(0, 4) + "\\n" + base64.substring(4);

        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/icon.png?ref=abc1234")))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {"type": "file", "encoding": "base64", "content": \"""" + multilineB64 + """
                        "}
                        """, MediaType.APPLICATION_JSON));

        byte[] content = adapter.getFileContent(tenantId, repoId, "icon.png", "abc1234");
        assertArrayEquals(binary, content);
    }

    @Test
    void getFileContent_missingFile_throwsDomainException() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/gone.java?ref=abc1234")))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND));

        DomainException ex = assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "gone.java", "abc1234"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void getFileContent_directoryPath_isRejected() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/src?ref=abc1234")))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "[{\"type\": \"file\", \"name\": \"App.java\"}]", MediaType.APPLICATION_JSON));

        DomainException ex = assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "src", "abc1234"));
        assertTrue(ex.getMessage().contains("directory"));
    }

    @Test
    void getFileContent_unsupportedEncoding_isRejected() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString(
                        "/repos/owner/test-repo/contents/symlink?ref=abc1234")))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"type\": \"symlink\", \"content\": \"eGFpbQ==\"}", MediaType.APPLICATION_JSON));

        DomainException ex = assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "symlink", "abc1234"));
        assertTrue(ex.getMessage().contains("not a regular file") || ex.getMessage().contains("directory"));
    }

    @Test
    void getFileContent_blankSha_rejectedWithoutHttpCall() {
        // No mock expectations configured: any HTTP call would fail the test.
        assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "src/App.java", "  "));
        assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "  ", "abc1234"));
    }

    @Test
    void getFileContent_neverFallsBackToBranchHead() {
        // Only the /repositories lookup is allowed; the requested SHA must appear in the
        // contents request and no request may target a branch name instead of the SHA.
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));
        mockServer.expect(MockRestRequestMatchers.requestTo(org.hamcrest.Matchers.containsString("?ref=requested-sha-999")))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND));

        assertThrows(DomainException.class,
                () -> adapter.getFileContent(tenantId, repoId, "src/App.java", "requested-sha-999"));
        mockServer.verify(); // fails if the adapter retried with another ref
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
    void publishStatusCheck_includesResponseBodyInException() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/repositories/12345"))
                .andRespond(MockRestResponseCreators.withSuccess("{\"full_name\": \"owner/test-repo\"}", MediaType.APPLICATION_JSON));

        mockServer.expect(MockRestRequestMatchers.requestTo("/repos/owner/test-repo/statuses/abc1234"))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"message\": \"Validation Failed\"}"));

        DomainException ex = assertThrows(DomainException.class, () -> adapter.publishStatusCheck(tenantId, repoId, "abc1234", DecisionOutcome.APPROVE, List.of(), "http://url"));
        assertTrue(ex.getMessage().contains("422 UNPROCESSABLE_ENTITY - {\"message\": \"Validation Failed\"}"));
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


    @Test
    void constructor_setsTimeoutsOnRestTemplate() {
        org.springframework.boot.web.client.RestTemplateBuilder builder = org.mockito.Mockito.mock(org.springframework.boot.web.client.RestTemplateBuilder.class);
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        org.mockito.Mockito.when(builder.setConnectTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
        org.mockito.Mockito.when(builder.setReadTimeout(org.mockito.ArgumentMatchers.any())).thenReturn(builder);
        org.mockito.Mockito.when(builder.defaultHeader(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
        org.mockito.Mockito.when(builder.rootUri(org.mockito.ArgumentMatchers.anyString())).thenReturn(builder);
        org.mockito.Mockito.when(builder.build()).thenReturn(restTemplate);

        new GithubSourceControlAdapter(repositoryRepository, "token", builder);

        org.mockito.Mockito.verify(builder).setConnectTimeout(java.time.Duration.ofSeconds(10));
        org.mockito.Mockito.verify(builder).setReadTimeout(java.time.Duration.ofSeconds(30));
    }

}
