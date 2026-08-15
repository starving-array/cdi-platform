package com.cdi.risk.domain;

import com.cdi.common.domain.id.EvidenceId;
import com.cdi.systemcontext.domain.CriticalityTier;

import java.util.List;
import java.util.Objects;

/**
 * Immutable input for the Deterministic Risk Engine.
 */
public record RiskAssessmentInput(
        int changedFilesCount,
        int linesAdded,
        int linesDeleted,
        boolean isDatabaseMigration,
        boolean isConfigurationChange,
        CriticalityTier serviceCriticality,
        int downstreamDependencyCount,
        EvidenceState evidenceState,
        List<EvidenceId> matchedIncidentEvidenceIds) {

    public RiskAssessmentInput {
        if (changedFilesCount < 0 || linesAdded < 0 || linesDeleted < 0 || downstreamDependencyCount < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
        Objects.requireNonNull(serviceCriticality, "Service Criticality cannot be null");
        Objects.requireNonNull(evidenceState, "Evidence state cannot be null");
        matchedIncidentEvidenceIds = matchedIncidentEvidenceIds == null ? List.of() : List.copyOf(matchedIncidentEvidenceIds);
    }
}
