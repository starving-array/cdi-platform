package com.cdi.investigation.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.EvidenceId;

import java.util.Objects;

/**
 * Evidence citation backing a single {@link InvestigationFinding}
 * (domain-model.md §F, ports-and-adapters.md §2.4).
 *
 * <p>Every factual finding must be traceable to a real {@link EvidenceId}
 * available to the application; the investigation must never fabricate
 * evidence. An optional verbatim snippet grounds the reference for the
 * reader.
 */
public record EvidenceCitation(EvidenceId evidenceId, String snippet) {

  public EvidenceCitation {
    if (evidenceId == null) {
      throw new DomainException("EvidenceId cannot be null");
    }
    snippet = snippet != null ? snippet.trim() : "";
  }

  public static EvidenceCitation of(EvidenceId evidenceId) {
    return new EvidenceCitation(evidenceId, "");
  }
}
