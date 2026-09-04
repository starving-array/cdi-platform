package com.cdi.application.analysis;

import com.cdi.common.domain.id.EvidenceId;
import java.util.List;
import java.util.Set;

/**
 * Application-level code intelligence derived from the changed code
 * (Parts 4-8 structural analysis), converted into domain-suitable DTOs
 * for LLM investigation prompting.
 * <p>
 * JavaParser AST objects are NOT exposed through the investigation boundary.
 * All structural information is converted into application-level models.
 */
public record InvestigationCodeIntelligence(
        String commitSha,
        List<String> changedFiles,
        List<String> changedMethodSignatures,
        Set<String> importedTypes,
        List<String> directCallers,
        List<String> directCallees,
        List<String> impactGraphEdges,
        List<String> dependencyPaths,
        Set<String> availabilityStates) {

    public InvestigationCodeIntelligence {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        changedMethodSignatures = changedMethodSignatures == null ? List.of() : List.copyOf(changedMethodSignatures);
        importedTypes = importedTypes == null ? Set.of() : Set.copyOf(importedTypes);
        directCallers = directCallers == null ? List.of() : List.copyOf(directCallers);
        directCallees = directCallees == null ? List.of() : List.copyOf(directCallees);
        impactGraphEdges = impactGraphEdges == null ? List.of() : List.copyOf(impactGraphEdges);
        dependencyPaths = dependencyPaths == null ? List.of() : List.copyOf(dependencyPaths);
        availabilityStates = availabilityStates == null ? Set.of() : Set.copyOf(availabilityStates);
    }
}