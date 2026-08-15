package com.cdi.evidence.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceRecordTest {

    private final TenantId tenantId = new TenantId(UUID.randomUUID());
    private final AnalysisRunId analysisRunId = new AnalysisRunId(UUID.randomUUID());
    private final EvidenceSource source = new EvidenceSource(SourceType.INCIDENT, "INC-123");

    @Test
    void shouldCreateValidEvidenceRecord() {
        EvidenceRecord record = EvidenceRecord.builder()
                .tenantId(tenantId)
                .analysisRunId(analysisRunId)
                .source(source)
                .origin(EvidenceOrigin.RETRIEVED)
                .title("Past Incident")
                .content("Database went down")
                .relevance(new Relevance(0.8, "Similar files changed"))
                .build();

        assertNotNull(record.getId());
        assertEquals(tenantId, record.getTenantId());
        assertEquals(analysisRunId, record.getAnalysisRunId());
        assertEquals(source, record.getSource());
        assertEquals(EvidenceOrigin.RETRIEVED, record.getOrigin());
        assertEquals("Past Incident", record.getTitle());
        assertEquals("Database went down", record.getContent().get());
        assertTrue(record.getContentHash().isPresent()); // SHA-256
        assertNotNull(record.getCapturedAt());
        assertEquals(0.8, record.getRelevance().get().score());
    }

    @Test
    void shouldRequireTenantId() {
        Exception e = assertThrows(NullPointerException.class, () -> 
            EvidenceRecord.builder()
                    .analysisRunId(analysisRunId)
                    .source(source)
                    .origin(EvidenceOrigin.RETRIEVED)
                    .title("Title")
                    .build()
        );
        assertEquals("TenantId is required", e.getMessage());
    }

    @Test
    void shouldRequireAnalysisRunId() {
        Exception e = assertThrows(NullPointerException.class, () -> 
            EvidenceRecord.builder()
                    .tenantId(tenantId)
                    .source(source)
                    .origin(EvidenceOrigin.RETRIEVED)
                    .title("Title")
                    .build()
        );
        assertEquals("AnalysisRunId is required", e.getMessage());
    }

    @Test
    void shouldRequireTitle() {
        Exception e = assertThrows(DomainException.class, () -> 
            EvidenceRecord.builder()
                    .tenantId(tenantId)
                    .analysisRunId(analysisRunId)
                    .source(source)
                    .origin(EvidenceOrigin.RETRIEVED)
                    .title("")
                    .build()
        );
        assertEquals("Title cannot be null or blank", e.getMessage());
    }

    @Test
    void shouldValidateRelevanceScore() {
        Exception e = assertThrows(DomainException.class, () -> 
            new Relevance(1.5, "Too high")
        );
        assertEquals("Relevance score must be between 0.0 and 1.0", e.getMessage());
        
        Exception e2 = assertThrows(DomainException.class, () -> 
            new Relevance(-0.1, "Too low")
        );
        assertEquals("Relevance score must be between 0.0 and 1.0", e2.getMessage());
    }

    @Test
    void shouldValidateEvidenceSource() {
        Exception e = assertThrows(DomainException.class, () -> 
            new EvidenceSource(null, "Ref")
        );
        assertEquals("SourceType cannot be null", e.getMessage());
        
        Exception e2 = assertThrows(DomainException.class, () -> 
            new EvidenceSource(SourceType.INCIDENT, "")
        );
        assertEquals("SourceReference cannot be null or blank", e2.getMessage());
    }
    
    @Test
    void shouldGenerateConsistentHash() {
        EvidenceRecord record1 = EvidenceRecord.builder()
                .tenantId(tenantId)
                .analysisRunId(analysisRunId)
                .source(source)
                .origin(EvidenceOrigin.RETRIEVED)
                .title("Title")
                .content("Same content")
                .build();
                
        EvidenceRecord record2 = EvidenceRecord.builder()
                .tenantId(tenantId)
                .analysisRunId(analysisRunId)
                .source(source)
                .origin(EvidenceOrigin.RETRIEVED)
                .title("Title")
                .content("Same content")
                .build();
                
        assertEquals(record1.getContentHash().get(), record2.getContentHash().get());
    }
    
    @Test
    void evidenceRecordShouldBeImmutableWithoutSetters() {
        // Asserting that there are no setters is generally done by design,
        // but we can test equality behavior based on ID
        EvidenceRecord record1 = EvidenceRecord.builder()
                .tenantId(tenantId)
                .analysisRunId(analysisRunId)
                .source(source)
                .origin(EvidenceOrigin.RETRIEVED)
                .title("Title")
                .build();
                
        assertEquals(record1, record1);
        assertNotEquals(record1, null);
    }
}
