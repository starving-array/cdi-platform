# Architecture Consistency Review

## 1. Executive Summary
This document provides the final architectural gate review for the V1 Engineering Change Decision Intelligence platform. The review confirms that the core architectural documentation successfully translates the product vision into a cohesive, modular monolith design. The strict separation between objective risk calculation, AI investigation, and deterministic policy enforcement is well-preserved across all layers (Domain, API, Persistence, and Workflow).

The architecture is **READY** for the implementation phase. A few minor/medium open questions remain regarding internal implementation choices, but no structural blockers exist.

---

## 2. Architecture Readiness Decision
**Verdict**: **READY**

The documentation provides a comprehensive, internally consistent "source of truth" that future AI coding agents can safely build upon without guessing the underlying architecture or prematurely introducing complex infrastructure.

---

## 3. Findings & Classifications

### 3.1 Blockers
*None discovered.* 
The core boundaries, tenant isolation, and fail-safe mechanisms are structurally sound.

### 3.2 High-Priority Findings
- **Missing Decision (Agent Internal Loop)**: The architecture specifies that `AgentPort` abstracts the LLM, and that the agent must cite evidence. However, it is not explicitly decided *how* the agent executes its queries to `EvidenceSearchPort` during its loop. The application layer must mediate these tools to ensure the LLM cannot bypass tenant boundaries when querying the vector DB.
  - *Recommendation*: Ensure the `AgentPort` implementation uses application-provided callback tools (e.g., Spring AI function calling) rather than direct database access.

### 3.3 Medium Findings
- **Missing Decision (Job Queue Framework)**: The MVP specifies asynchronous background workers managed by a `JobQueuePort` and persisted in an `analysis_job` PostgreSQL table. The specific library (e.g., Spring Batch, JobRunr, or a custom polling loop) is unselected.
  - *Recommendation*: Select JobRunr or a simple custom Postgres polling executor for V1 to keep dependencies low.
- **Missing Decision (Policy Engine Syntax)**: We have deferred selecting a policy syntax (CEL, SpEL, Rego). 
  - *Recommendation*: Make a fast decision early in implementation (CEL is recommended for security and simplicity within Java).

### 3.4 Low Findings
- **Event Naming Validation**: `domain-events.md` defines `ChangeProposed`, which aligns with the workflow. We must ensure webhook intake logic standardizes all SCM events into this single trigger.
- **SSE / WebSockets**: The V1 API relies on client polling (`GET /analysis-runs/{id}`) for async status updates. 
  - *Recommendation*: Keep polling for MVP; defer WebSockets to V2.

---

## 4. Contradictions Between Documents
- *No critical contradictions found.* The domain model aggregate `Change` mapping to `change` and `AnalysisRun` mapping to `analysis_run` successfully resolves early ambiguity between "a PR" and "a specific commit evaluation." The workflow, API, and Data Model all consistently respect this distinction.

---

## 5. Security Concerns
- **Tenant Isolation**: The requirement for `tenant_id` on every table and in every foreign key provides an excellent structural defense.
- **AI Prompt Injection**: Because the agent operates on PR diffs and descriptions (user-provided text), prompt injection is a risk.
  - *Mitigation*: The architectural fail-safe is strong here—the Agent cannot approve a PR. It only produces findings, which are then run through deterministic policy. Prompt injection cannot bypass the deterministic rules.

---

## 6. Scalability Concerns
- The decision to use a modular monolith with PostgreSQL (`pgvector` + simple queue) is highly appropriate for V1. It aggressively prevents premature microservice sprawl.
- **Vector Indexing**: The data model correctly separates `evidence_embedding` from `evidence_record`. This allows rebuilding vectors without losing historical text if we migrate embedding models later.

---

## 7. AI Trust Concerns
- **Fail-Safe Enforced**: The workflow correctly mandates that if the AI times out or fails structural validation, the system degrades gracefully and relies on the deterministic risk score. AI failures do not equate to "safe."
- **Traceability**: The requirement that AI findings must be cited via `EvidenceCitation` (pointing to an immutable `EvidenceRecord`) strongly mitigates hallucination.

---

## 8. Vibe-Coding Readiness
The repository is highly prepared for AI-assisted implementation ("vibe-coding"). Future coding prompts can direct the agent to implement specific bounded contexts (e.g., "Implement the Change Context") while pointing to `bounded-contexts.md`, `domain-model.md`, and `use-cases.md`. The AI will not have to guess the persistence strategy, the multi-tenancy model, or the ID generation strategy (UUIDv7).

---

## 9. Recommended Next Steps for Implementation
1. **Scaffold the Project**: Generate the Spring Boot multi-module (or package-by-feature) skeleton matching the Bounded Contexts.
2. **Setup Infrastructure Adapters**: Configure PostgreSQL, Flyway, and the initial schemas based on `data-model.md`.
3. **Implement Domain Core**: Build the `Risk`, `Policy`, and `Decision` logic without connecting APIs or external LLMs yet.
4. **Build Ports**: Implement the generic `SourceControlPort` (starting with GitHub) and the `AgentPort`.
