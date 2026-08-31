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
public class GithubSourceControlAdapter implements SourceControlPort {

    private final RepositoryRepository repositoryRepository;
    private final RestTemplate restTemplate;

    public GithubSourceControlAdapter(
            RepositoryRepository repositoryRepository,
            @Value("${cdi.github.token}") String githubToken,
            RestTemplateBuilder restTemplateBuilder) {
        this.repositoryRepository = repositoryRepository;
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
                diffs.add(new FileDiff(file.filename(), file.additions(), file.deletions(), changeType));
            }
            return diffs;
        } catch (RestClientResponseException e) {
            throw new DomainException("Failed to fetch diff from GitHub: " + e.getStatusCode());
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

    record GithubFile(String filename, int additions, int deletions, String status) {}
    record GithubCommitResponse(List<GithubFile> files) {}

    record GithubStatusRequest(String state, String target_url, String description, String context) {}
}
