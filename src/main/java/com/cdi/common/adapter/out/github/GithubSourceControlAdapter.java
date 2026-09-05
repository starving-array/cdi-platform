package com.cdi.common.adapter.out.github;

import com.cdi.analysis.domain.FileDiff;
import com.cdi.application.port.out.ChangeMetadata;
import com.cdi.application.port.out.RepositoryRepository;
import com.cdi.application.port.out.SourceControlPort;
import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.decision.domain.DecisionOutcome;
import com.cdi.decision.domain.DecisionReason;
import com.cdi.repository.domain.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnExpression("!'${cdi.github.token:}'.isEmpty()")
@org.springframework.context.annotation.Profile("!test")
public class GithubSourceControlAdapter implements SourceControlPort {

    private final RepositoryRepository repositoryRepository;
    private final RestTemplate restTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public GithubSourceControlAdapter(
            RepositoryRepository repositoryRepository,
            @Value("${cdi.github.token}") String githubToken,
            RestTemplateBuilder restTemplateBuilder) {
        this(repositoryRepository, githubToken, restTemplateBuilder,
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GithubSourceControlAdapter(
            RepositoryRepository repositoryRepository,
            @Value("${cdi.github.token}") String githubToken,
            RestTemplateBuilder restTemplateBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.repositoryRepository = repositoryRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .defaultHeader("Authorization", "Bearer " + githubToken)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .rootUri("https://api.github.com")
                .build();
    }

    private Repository getRepository(TenantId tenantId, RepositoryId repositoryId) {
        return repositoryRepository.findByTenantIdAndId(tenantId, repositoryId)
                .orElseThrow(() -> new DomainException("Repository not found"));
    }

    private String getFullName(Repository repo) {
        if (repo.getProviderType() != Repository.ProviderType.GITHUB) {
            throw new DomainException("Unsupported provider: " + repo.getProviderType());
        }
        // Fetch full name via /repositories/{id}
        String repoId = repo.getExternalId();
        try {
            ResponseEntity<GithubRepoResponse> response = restTemplate.getForEntity(
                    "/repositories/{id}", GithubRepoResponse.class, repoId);
            if (response.getBody() == null || response.getBody().full_name() == null) {
                throw new DomainException("Could not resolve repository full name");
            }
            return response.getBody().full_name();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new DomainException("GitHub repository not found: " + repoId);
            }
            throw new DomainException("Error fetching repository: " + e.getStatusCode());
        }
    }

    @Override
    public ChangeMetadata getChangeMetadata(TenantId tenantId, RepositoryId repositoryId, String providerChangeId) {
        Repository repo = getRepository(tenantId, repositoryId);
        String fullName = getFullName(repo);
        String[] parts = fullName.split("/");
        if (parts.length != 2) throw new DomainException("Invalid repository full name");

        try {
            ResponseEntity<GithubPullResponse> response = restTemplate.getForEntity(
                    "/repos/{owner}/{repo}/pulls/{pullNumber}", GithubPullResponse.class, parts[0], parts[1], providerChangeId);

            GithubPullResponse pr = response.getBody();
            if (pr == null) throw new DomainException("Empty response for PR metadata");

            String author = (pr.user() != null) ? pr.user().login() : "unknown";
            String title = (pr.title() != null) ? pr.title() : "";
            String desc = (pr.body() != null) ? pr.body() : "";
            String sourceBranch = (pr.head() != null) ? pr.head().ref() : "";
            String latestCommitSha = (pr.head() != null) ? pr.head().sha() : "";
            String targetBranch = (pr.base() != null) ? pr.base().ref() : "";

            return new ChangeMetadata(
                    providerChangeId,
                    title,
                    desc,
                    author,
                    sourceBranch,
                    targetBranch,
                    latestCommitSha
            );
        } catch (RestClientResponseException e) {
            throw new DomainException("Failed to fetch PR metadata from GitHub: " + e.getStatusCode());
        }
    }

    @Override
    public List<FileDiff> getDiff(TenantId tenantId, RepositoryId repositoryId, String commitSha) {
        Repository repo = getRepository(tenantId, repositoryId);
        String fullName = getFullName(repo);
        String[] parts = fullName.split("/");
        if (parts.length != 2) throw new DomainException("Invalid repository full name");

        try {
            ResponseEntity<GithubCommitResponse> response = restTemplate.getForEntity(
                    "/repos/{owner}/{repo}/commits/{ref}", GithubCommitResponse.class, parts[0], parts[1], commitSha);

            GithubCommitResponse commit = response.getBody();
            if (commit == null || commit.files() == null) {
                return List.of();
            }

            List<FileDiff> diffs = new ArrayList<>();
            for (GithubFile file : commit.files()) {
                FileDiff.ChangeType changeType = switch (file.status()) {
                    case "added", "copied" -> FileDiff.ChangeType.ADDED;
                    case "removed" -> FileDiff.ChangeType.DELETED;
                    default -> FileDiff.ChangeType.MODIFIED;
                };
                diffs.add(new FileDiff(file.filename(), file.additions(), file.deletions(), changeType, file.patch()));
            }
            return diffs;
        } catch (RestClientResponseException e) {
            throw new DomainException("Failed to fetch diff from GitHub: " + e.getStatusCode());
        }
    }

    /**
     * Retrieves one file's raw bytes from the GitHub Contents API at the
     * EXACT requested commit. The SHA is sent as {@code ?ref=<sha>}; on any
     * failure (404, directory path, unsupported encoding) a
     * {@link DomainException} is thrown — no fallback ref is ever attempted.
     */
    @Override
    public byte[] getFileContent(TenantId tenantId, RepositoryId repositoryId, String path, String commitSha) {
        if (path == null || path.isBlank()) {
            throw new DomainException("File path cannot be blank");
        }
        if (commitSha == null || commitSha.isBlank()) {
            throw new DomainException("Commit SHA cannot be blank");
        }
        Repository repo = getRepository(tenantId, repositoryId);
        String fullName = getFullName(repo);
        String[] parts = fullName.split("/");
        if (parts.length != 2) throw new DomainException("Invalid repository full name");

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/repos/{owner}/{repo}/contents/{path}?ref={sha}", String.class,
                    parts[0], parts[1], path.trim(), commitSha.trim());

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new DomainException("Empty content response for file: " + path);
            }
            return decodeContentsApiBody(body, path);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new DomainException("File not found at requested commit: " + path);
            }
            throw new DomainException("Error fetching file content: " + e.getStatusCode());
        }
    }

    private byte[] decodeContentsApiBody(String body, String path) {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            // The Contents API returns a JSON array for directories.
            throw new DomainException("Path is a directory, not a file: " + path);
        }
        final com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = objectMapper.readTree(trimmed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new DomainException("Invalid content response from GitHub");
        }
        String type = node.path("type").asText("");
        if (!"file".equals(type)) {
            throw new DomainException("Path is not a regular file (type=" + type + "): " + path);
        }
        String encoding = node.path("encoding").asText("");
        if (!"base64".equals(encoding)) {
            throw new DomainException("Unsupported content encoding: " + encoding);
        }
        String encoded = node.path("content").asText("").replaceAll("\\s", "");
        try {
            return java.util.Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new DomainException("Could not decode file content: " + path);
        }
    }

    @Override
    public void publishStatusCheck(TenantId tenantId, RepositoryId repositoryId, String commitSha, DecisionOutcome outcome, List<DecisionReason> reasons, String detailsUrl) {
        Repository repo = getRepository(tenantId, repositoryId);
        String fullName = getFullName(repo);
        String[] parts = fullName.split("/");
        if (parts.length != 2) throw new DomainException("Invalid repository full name");

        String state = switch (outcome) {
            case APPROVE -> "success";
            case REVIEW_REQUIRED -> "pending";
            case BLOCK -> "failure";
        };

        String description = "CDI Policy: " + outcome.name();

        GithubStatusRequest request = new GithubStatusRequest(state, detailsUrl, description, "cdi/policy-check");

        try {
            restTemplate.postForEntity(
                    "/repos/{owner}/{repo}/statuses/{sha}", request, Void.class, parts[0], parts[1], commitSha);
        } catch (RestClientResponseException e) {
            throw new DomainException("Failed to publish status to GitHub: " + e.getStatusCode());
        }
    }
    // --- DTOs ---

    record GithubRepoResponse(String full_name) {}

    record GithubUser(String login) {}
    record GithubRef(String ref, String sha) {}
    record GithubPullResponse(String title, String body, GithubUser user, GithubRef head, GithubRef base) {}

    record GithubFile(String filename, int additions, int deletions, String status, String patch) {}
    record GithubCommitResponse(List<GithubFile> files) {}

    record GithubStatusRequest(String state, String target_url, String description, String context) {}
}
