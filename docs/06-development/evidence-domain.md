# Evidence Domain

This document describes the Evidence domain implementation for the Engineering Change Decision Intelligence platform.

## 1. Evidence Responsibilities
The Evidence context is the system's memory. It represents an immutable observable fact or source that can support reasoning. Evidence is explicitly NOT a decision, a generic document, a risk assessment, or an LLM response. 

## 2. Evidence Identity
Every piece of evidence is identifiable via `EvidenceId`. It is modeled as an Aggregate Root (`EvidenceRecord`).

## 3. Evidence Ownership
Evidence is owned by a Tenant (`TenantId`) and is scoped to a specific analysis evaluation (`AnalysisRunId`). It references `AnalysisRunId` by its identity rather than embedding the entire AnalysisRun aggregate, keeping boundaries clean and permitting evidence reuse/retrieval in the future.

## 4. Evidence Sources
Evidence must be traceable. It requires a provider-neutral `EvidenceSource` containing a `SourceType` (e.g., INCIDENT, PR, DEPENDENCY) and a `sourceReference` (a URI or unique string). It avoids relying on arbitrary GitHub APIs or integration models.

## 5. Evidence Origins
An `EvidenceOrigin` classifies how the evidence was found:
- `DETERMINISTIC`: Derived from known facts.
- `RETRIEVED`: Looked up from historical/system sources (RAG, API).
- `AGENT_DISCOVERED`: Discovered by the AI agent during an investigation.

## 6. Traceability
Each `EvidenceRecord` tracks its identity, tenant, run, origin, title, and timestamp. It ensures that when evidence is cited by an agent, the human reviewer can trace it exactly back to the source fact at the time of capture.

## 7. Immutability
`EvidenceRecord` is strictly immutable after capture. There are no public setters. This preserves the state of what evidence existed and was observed when a decision was rendered.

## 8. Relevance
Evidence may carry a `Relevance` object containing a score (0.0 - 1.0) and a reason. This specifies how useful the evidence is to the *current* analysis, distinguishing it from "Risk" which measures danger.

## 9. Agent-Discovered Evidence
Evidence discovered by agents becomes a standard `EvidenceRecord` bound to the same strict validation (requires valid source references, tenant scoping, and immutable hashes).

## 10. Relationship to Embeddings
Vectors/embeddings are intentionally omitted from the `EvidenceRecord` domain. Embeddings are infrastructure-level functionality to support semantic search. The domain strictly represents the source content and hash.

## 11. Important Invariants
- `EvidenceRecord` must have a valid `TenantId`, `AnalysisRunId`, and `EvidenceSource`.
- Content integrity is preserved using a SHA-256 `contentHash`.
- Cross-tenant data sharing is prevented inherently via `TenantId` inclusion on all records.
