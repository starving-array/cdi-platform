package com.cdi.evidence.adapter.out.persistence;

import com.cdi.application.common.error.PortException;
import com.cdi.application.common.error.PortType;
import com.cdi.application.port.out.EmbeddingPort;
import com.cdi.application.port.out.EvidenceRepository;
import com.cdi.application.port.out.EvidenceSearchPort;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA/PostgreSQL pgvector adapter backing the evidence store and
 * semantic-first {@code EvidenceSearchPort.searchByQuery} with deterministic
 * fallback (D1-D5, ADR-003, ADR-008).
 */
@Repository
public class JpaEvidenceRepository implements EvidenceRepository, EvidenceSearchPort {

  private final EvidenceJpaRepository evidenceJpaRepository;
  private final EmbeddingPort embeddingPort;
  private final JdbcTemplate jdbcTemplate;
  private final double similarityThreshold;

  public JpaEvidenceRepository(
      EvidenceJpaRepository evidenceJpaRepository,
      EmbeddingPort embeddingPort,
      JdbcTemplate jdbcTemplate,
      @Value("${cdi.evidence.similarity-threshold:0.65}") double similarityThreshold) {
    this.evidenceJpaRepository = evidenceJpaRepository;
    this.embeddingPort = embeddingPort;
    this.jdbcTemplate = jdbcTemplate;
    this.similarityThreshold = similarityThreshold;
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchByQuery(TenantId tenantId, String query, int limit) {
    // 1. Attempt Semantic pgvector retrieval
    try {
      List<Float> queryEmbedding = embeddingPort.embedText(query);
      String vectorStr = formatVector(queryEmbedding);

      List<EvidenceEntity> entities =
          evidenceJpaRepository.searchByVector(tenantId.value(), vectorStr, similarityThreshold, limit);

      if (!entities.isEmpty()) {
        return entities.stream().map(EvidenceMapper::toDomain).toList();
      }
    } catch (Exception e) {
      // Semantic retrieval failed -> fallback to deterministic ILIKE search below
    }

    // 2. Deterministic ILIKE Fallback
    try {
      return evidenceJpaRepository
          .searchByQuery(tenantId.value(), wrapLike(query), limit)
          .stream()
          .map(EvidenceMapper::toDomain)
          .toList();
    } catch (RuntimeException e) {
      throw new PortException(
          PortType.EVIDENCE_SEARCH, false, "Evidence retrieval failed", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchSimilarChanges(
      TenantId tenantId, List<String> filePaths, int limit) {
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRecord> searchIncidents(
      TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit) {
    return List.of();
  }

  @Override
  @Transactional
  public EvidenceRecord save(EvidenceRecord record) {
    EvidenceEntity entity = EvidenceMapper.toEntity(record);
    EvidenceEntity savedEntity = evidenceJpaRepository.saveAndFlush(entity);

    // Generate and persist pgvector embedding (D5)
    String textToEmbed = buildEmbeddableText(record);
    List<Float> vector = embeddingPort.embedText(textToEmbed);
    String vectorStr = formatVector(vector);

    UUID embeddingId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO evidence_embedding (id, tenant_id, evidence_record_id, chunk_index, embedding, model_version) "
            + "VALUES (?, ?, ?, 0, cast(? as vector), ?) "
            + "ON CONFLICT (tenant_id, evidence_record_id, chunk_index) "
            + "DO UPDATE SET embedding = cast(EXCLUDED.embedding as vector), model_version = EXCLUDED.model_version",
        embeddingId,
        record.getTenantId().value(),
        record.getId().value(),
        vectorStr,
        embeddingPort.getModelVersion());

    return EvidenceMapper.toDomain(savedEntity);
  }

  private static String buildEmbeddableText(EvidenceRecord record) {
    String content = record.getContent().orElse("");
    return record.getTitle() + "\n" + content;
  }

  private static String formatVector(List<Float> vector) {
    return "["
        + vector.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","))
        + "]";
  }

  private static String wrapLike(String query) {
    return "%" + query + "%";
  }
}