package com.cdi.evidence.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.AnalysisRunId;
import com.cdi.common.domain.id.EvidenceId;
import com.cdi.common.domain.id.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable EvidenceRecord Aggregate Root.
 * Represents an observable fact/source that supports reasoning.
 */
public class EvidenceRecord {

    private final EvidenceId id;
    private final TenantId tenantId;
    private final AnalysisRunId analysisRunId;
    private final EvidenceSource source;
    private final EvidenceOrigin origin;
    
    private final String title;
    private final String content;
    private final String contentHash;
    
    private final Instant capturedAt;
    private final Instant sourceTimestamp; // Optional
    
    private final Relevance relevance; // Optional

    private EvidenceRecord(Builder builder) {
        this.id = builder.id != null ? builder.id : EvidenceId.generate();
        this.tenantId = Objects.requireNonNull(builder.tenantId, "TenantId is required");
        this.analysisRunId = Objects.requireNonNull(builder.analysisRunId, "AnalysisRunId is required");
        this.source = Objects.requireNonNull(builder.source, "EvidenceSource is required");
        this.origin = Objects.requireNonNull(builder.origin, "EvidenceOrigin is required");
        
        this.title = builder.title;
        if (this.title == null || this.title.isBlank()) {
            throw new DomainException("Title cannot be null or blank");
        }
        
        this.content = builder.content;
        this.contentHash = generateHash(builder.content);
        
        this.capturedAt = builder.capturedAt != null ? builder.capturedAt : Instant.now();
        this.sourceTimestamp = builder.sourceTimestamp;
        this.relevance = builder.relevance;
    }

    private String generateHash(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new DomainException("Unable to generate content hash", e);
        }
    }

    public EvidenceId getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public AnalysisRunId getAnalysisRunId() { return analysisRunId; }
    public EvidenceSource getSource() { return source; }
    public EvidenceOrigin getOrigin() { return origin; }
    public String getTitle() { return title; }
    public Optional<String> getContent() { return Optional.ofNullable(content); }
    public Optional<String> getContentHash() { return Optional.ofNullable(contentHash); }
    public Instant getCapturedAt() { return capturedAt; }
    public Optional<Instant> getSourceTimestamp() { return Optional.ofNullable(sourceTimestamp); }
    public Optional<Relevance> getRelevance() { return Optional.ofNullable(relevance); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvidenceRecord that = (EvidenceRecord) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvidenceId id;
        private TenantId tenantId;
        private AnalysisRunId analysisRunId;
        private EvidenceSource source;
        private EvidenceOrigin origin;
        private String title;
        private String content;
        private Instant capturedAt;
        private Instant sourceTimestamp;
        private Relevance relevance;

        public Builder id(EvidenceId id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder analysisRunId(AnalysisRunId analysisRunId) {
            this.analysisRunId = analysisRunId;
            return this;
        }

        public Builder source(EvidenceSource source) {
            this.source = source;
            return this;
        }

        public Builder origin(EvidenceOrigin origin) {
            this.origin = origin;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder capturedAt(Instant capturedAt) {
            this.capturedAt = capturedAt;
            return this;
        }

        public Builder sourceTimestamp(Instant sourceTimestamp) {
            this.sourceTimestamp = sourceTimestamp;
            return this;
        }

        public Builder relevance(Relevance relevance) {
            this.relevance = relevance;
            return this;
        }

        public EvidenceRecord build() {
            return new EvidenceRecord(this);
        }
    }
}
