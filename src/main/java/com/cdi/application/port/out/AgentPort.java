package com.cdi.application.port.out;

import com.cdi.common.domain.id.TenantId;
import com.cdi.evidence.domain.EvidenceRecord;
import com.cdi.risk.domain.RiskAssessment;

import java.util.List;

/**
 * Outbound port to the AI risk investigation agent (ports-and-adapters.md
 * §2.4, application-layer.md §7). Declared now; first used in P1 by
 * {@code InvestigateRisk}.
 *
 * <p>Returns strongly typed, structured findings. The port never exposes
 * LangChain/OpenAI/Claude/Gemini types; the application layer structurally
 * validates the output (evidence citations must reference real
 * {@code EvidenceId}s) before accepting it.
 *
 * <p><b>Failure policy:</b> exactly one immediate retry (2-minute timeout);
 * on exhaustion the worker degrades gracefully — investigation FAILED and
 * policy proceeds using only the deterministic {@link RiskAssessment}.
 */
public interface AgentPort {

  InvestigationFindings investigate(
      AgentContext context,
      RiskAssessment riskAssessment,
      List<EvidenceRecord> evidence);
}