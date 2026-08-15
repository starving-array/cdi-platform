package com.cdi.analysis.domain;

import com.cdi.common.domain.exception.DomainException;

/**
 * Value object identifying the exact code state evaluated by an AnalysisRun.
 */
public record CodeSnapshot(String commitSha, String branch) {
    public CodeSnapshot {
        if (commitSha == null || commitSha.isBlank()) {
            throw new DomainException("Commit SHA cannot be blank");
        }
        // Branch can be null/blank in some detached-head scenarios, but trim it if present
        commitSha = commitSha.trim();
        branch = branch != null ? branch.trim() : "";
    }
}
