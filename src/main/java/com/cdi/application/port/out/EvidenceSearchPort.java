package com.cdi.application.port.out;

import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;

import java.util.List;

/**
 * Outbound port retrieving historical truth — past changes and incidents —
 * relevant to the current change (ports-and-adapters.md §2.3,
 * application-layer.md §7).
 *
 * <p>The application layer does not know whether the adapter uses SQL
 * {@code ILIKE}, pgvector semantic search, or an external vector store.
 *
 * <p><b>Failure policy:</b> no retry; the worker gracefully degrades to zero
 * evidence and the risk assessment reflects the missing evidence state via
 * deterministic signals only.
 */
public interface EvidenceSearchPort {

  List<EvidenceRecord> searchSimilarChanges(TenantId tenantId, List<String> filePaths, int limit);

  List<EvidenceRecord> searchIncidents(TenantId tenantId, ServiceId serviceId, List<String> keywords, int limit);
}