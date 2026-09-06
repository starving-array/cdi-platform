# Engineering Constitution

## Architectural Principles
1. KISS
2. DRY
3. YAGNI
4. SOLID
5. Separation of concerns
6. Domain-driven boundaries
7. Hexagonal/ports-and-adapters principles
8. Modular monolith for MVP
9. Evolutionary architecture
10. Evidence before confidence
11. Deterministic systems enforce safety
12. AI proposes and reasons
13. Humans retain control over consequential actions
14. Every important decision must be auditable
15. Scalability decisions must be based on measured bottlenecks

## AI Principles
- Use the correct tool for the problem (Deterministic, ML, RAG, LLM, Agent).
- **Deterministic**: diff analysis, LOC, changed files, API changes, schema changes, dependency analysis, service criticality, test coverage, policy evaluation, validation, authorization.
- **ML**: risk probability, classification, calibration, historical prediction.
- **RAG**: incidents, postmortems, historical changes, architecture documentation, runbooks, similar changes.
- **LLM**: semantic interpretation, evidence synthesis, explanation, reasoning, investigation planning.
- **AGENT**: Only when active investigation or multi-step reasoning is required. Do NOT create artificial agents for deterministic tasks.

## Security Principles
- Deterministic systems enforce safety. AI proposes and reasons, but humans retain control over consequential actions.

## Vibe-Coding Workflow
- Documentation is a first-class engineering artifact.
- The repository must contain a clear source of truth.
- Every implementation prompt should reference: project charter, engineering constitution, architecture, domain model, API contracts, acceptance criteria, testing requirements.
- AI-generated code must not be allowed to silently change architectural decisions.
