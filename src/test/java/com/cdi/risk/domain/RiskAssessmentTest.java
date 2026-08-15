package com.cdi.risk.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.RiskAssessmentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskAssessmentTest {

    @Test
    void shouldRejectInvalidRiskAssessmentConstruction() {
        assertThrows(NullPointerException.class, () -> new RiskAssessment(
                null,
                AnalysisRunId.generate(),
                RiskScore.of(50),
                RiskLevel.MEDIUM,
                List.of(),
                EvidenceState.EVIDENCE_AVAILABLE,
                "v1",
                Instant.now()
        ));

        assertThrows(DomainException.class, () -> new RiskAssessment(
                RiskAssessmentId.generate(),
                AnalysisRunId.generate(),
                RiskScore.of(50),
                RiskLevel.MEDIUM,
                List.of(),
                EvidenceState.EVIDENCE_AVAILABLE,
                "",
                Instant.now()
        ));
    }
}
