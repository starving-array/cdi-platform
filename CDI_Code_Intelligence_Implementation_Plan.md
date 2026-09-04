# CDI Code-Intelligence Implementation Plan

## Purpose

Complete the missing code-intelligence layer in the CDI project so PR risk analysis is based on the actual changed code plus relevant repository/dependency context, rather than only commit metadata and file statistics.

This document is intended for OpenCode. Implement it part by part, run tests after every part, and avoid unnecessary changes to the already-working UC-01 -> UC-06 workflow.

---

# 0. Current State

The existing workflow is:

PR / Change proposal
-> AnalysisRun
-> AnalyzeChange
-> InvestigateRisk
-> EvaluatePolicy
-> DecisionRecord
-> GenerateDecision
-> GitHub status

Already demonstrated as working:
- GitHub authentication
- GitHub repository lookup
- PR/change creation
- analysis run creation/completion
- risk persistence
- policy decision persistence
- GitHub commit status publication
- in-process async command dispatch

Important gaps already observed:

### GithubSourceControlAdapter

The current getDiff() maps GitHub files to:
- filename
- additions
- deletions
- change type

It does not preserve actual patch content.

### Agent implementation

The local AgentPort implementation currently returns:

    return new InvestigationFindings(List.of());

Therefore meaningful AI/LLM investigation is not currently implemented by that adapter.

### Risk engine

The deterministic risk engine already calculates the final risk. Keep this deterministic. The LLM should produce structured investigation findings/evidence, not directly invent the final numeric score.

### Policy / decision

Do not rewrite PolicyEngine, EvaluatePolicyHandler, GenerateDecisionHandler, or the queue dispatcher unless tests reveal a concrete defect.

---

# 1. Principles

OpenCode MUST:

1. Work incrementally.
2. Inspect existing interfaces/classes before changing them.
3. Reuse existing domain concepts and ports where possible.
4. Preserve domain/application/adapter boundaries.
5. Keep GitHub API calls inside the source-control adapter.
6. Keep final risk scoring deterministic.
7. Keep LLM output structured and evidence-backed.
8. Never blindly send an entire large repository to an LLM.
9. Analyze source from the exact requested commit SHA.
10. Never mix files from different commits.
11. Never put secrets in code, tests, or logs.
12. Add focused tests for new behavior.
13. Run the existing test suite after every part.
14. Stop and report when a significant architectural decision is required rather than silently inventing one.

---

# 2. Target Architecture

GitHub PR
-> Change proposal
-> AnalysisRun
-> AnalyzeChange
-> GitHub patch/source retrieval
-> CodeContext construction
   - changed files
   - patches
   - changed classes/methods
   - imports
   - direct dependencies
   - callers/callees where feasible
   - relevant source
   - system context
-> InvestigateRisk
-> Evidence retrieval
-> LLM / Agent
-> structured InvestigationFindings
-> DeterministicRiskEngine
-> RiskAssessment
-> EvaluatePolicy
-> DecisionRecord
-> GenerateDecision
-> GitHub status

Separation of responsibility:

Repository/code analysis = context building
LLM = semantic reasoning
Risk engine = deterministic scoring
Policy engine = deterministic decision

---

# PART 1 - Inspect Existing Implementation

## Goal

Understand what already exists before adding new classes.

Inspect:
- AnalyzeChangeHandler
- InvestigateRiskHandler
- EvaluatePolicyHandler
- GenerateDecisionHandler
- FileDiff
- AnalysisRun
- RiskAssessment
- InvestigationFindings
- AgentContext
- AgentPort
- SourceControlPort
- EvidenceSearchPort
- GithubSourceControlAdapter
- all AI/LLM adapters
- all evidence/RAG adapters
- Maven dependencies
- existing tests

Search for:
- AgentPort
- AgentContext
- InvestigationFindings
- FileDiff
- getDiff
- getChangeMetadata
- EvidenceSearchPort

## Deliverable

Create a short implementation map if useful:

existing component -> responsibility -> required modification

Do not make speculative production changes.

## Acceptance

- Existing flow is understood.
- Existing interfaces are reused where possible.
- Existing tests pass.

---

# PART 2 - Preserve Actual GitHub Patch Content

## Goal

Return actual patch content instead of only file statistics.

Extend FileDiff or introduce the smallest appropriate representation so a changed file can contain:

- filename
- additions
- deletions
- change type
- patch

Map GitHub's patch field.

Handle:
- modified files
- added files
- deleted files
- renamed/copied files
- missing/null patch
- binary files

A missing textual patch must not crash the entire analysis.

## Tests

Add tests for:
1. modified file with patch
2. added file
3. deleted file
4. renamed/copied file
5. null/missing patch
6. binary/no textual patch

## Acceptance

Given a commit SHA, CDI can obtain filename + status + additions + deletions + patch without breaking existing callers.

---

# PART 3 - Retrieve Source Files at the Exact Commit

## Goal

Retrieve source content for relevant files at the analyzed commit SHA.

Extend SourceControlPort only if necessary, for example conceptually:

    getFileContent(tenantId, repositoryId, path, commitSha)

or a suitable batch equivalent.

Use GitHub Contents/Git Blob APIs through GithubSourceControlAdapter.

The requested SHA MUST be used. Never silently use the current default branch.

Handle:
- text files
- missing files
- binary files
- large files
- GitHub API errors

Keep GitHub DTOs outside the domain.

## Tests

Verify:
- requested SHA is sent
- requested path is sent
- content is decoded correctly
- missing file is handled
- GitHub errors are translated consistently

---

# PART 4 - Introduce CodeContext

## Goal

Represent the code relevant to a change.

Conceptually:

    CodeContext
     - commitSha
     - changedFiles
     - changedMethods
     - changedClasses
     - dependencies
     - callers
     - callees
     - relevantSource

Use explicit types rather than a giant generic map.

Do not put GitHub-specific DTOs into CodeContext.

## Acceptance

A completed analysis can carry:
- exact analyzed commit
- actual patches
- relevant source
- discovered structural relationships

---

# PART 5 - Detect Changed Classes and Methods

## Goal

Identify affected Java classes/methods.

Prefer an existing AST/parser dependency if available. If none exists, introduce the smallest suitable parser.

Do not build a fragile regex-only Java parser.

At minimum identify:
- package
- imports
- classes/interfaces/enums
- methods
- constructors
- fields
- method signatures

Map patch hunks to relevant class/method where reasonably possible.

Gracefully fall back to file-level context if method mapping cannot be determined.

## Tests

Create Java fixtures covering:
- one class/one method
- multiple methods
- nested classes
- constructors
- interfaces
- annotations
- overloaded methods

---

# PART 6 - Discover Imports and Direct Dependencies

## Goal

Build basic structural context.

For each changed Java class identify:
- imports
- fields/types
- implemented interfaces
- extended classes
- direct method-call targets where feasible

Example:

    PaymentService
     - FraudService
     - PaymentRepository
     - PaymentGateway

Start with direct dependencies. Do not crawl the whole repository.

---

# PART 7 - Discover Callers and Callees

## Goal

Estimate blast radius.

For a changed method such as:

    PaymentService.authorize()

try to discover:

    callees:
      FraudService.validate()
      PaymentGateway.authorize()

    callers:
      PaymentController.createPayment()
      SubscriptionService.charge()

Use a bounded approach, initially depth=1.

If exact discovery is unreliable, return unknown/not-discovered rather than inventing relationships.

## Acceptance

The context can answer:
- What does the changed method call?
- Where feasible, who calls the changed method?

---

# PART 8 - Build the Relevant Source / Impact Cone

## Goal

Retrieve only source likely relevant to the change.

Example:

Changed:
    PaymentService.authorize()

Relevant:
    PaymentService.java
    FraudService.java
    FraudClient.java
    PaymentGateway.java
    PaymentController.java

Do not send an entire repository.

Start from changed classes/methods, then include:
- direct dependencies
- direct callers
- important interfaces
- relevant configuration

Add configurable limits:
- maximum files
- maximum bytes/tokens
- maximum dependency depth

If configuration already exists, follow project conventions.

---

# PART 9 - Extend AgentContext

## Goal

Give InvestigateRisk meaningful context.

The context should contain, where appropriate:
- change
- risk assessment
- diff
- code context
- system context
- evidence

The agent should be able to reason about:
- behavioral changes
- dependency failures
- API contract changes
- database impact
- concurrency
- security
- performance
- compatibility
- testing gaps
- blast radius

The LLM must distinguish:
- observed evidence
- inference
- unknown

---

# PART 10 - Implement a Real Agent / LLM Adapter

## Goal

Replace the empty local AgentPort implementation with a real adapter.

The adapter MUST:
1. Receive structured AgentContext.
2. Build a bounded prompt.
3. Include actual diff and relevant source.
4. Include evidence.
5. Instruct the model not to invent facts.
6. Require structured output.
7. Parse and validate the response.
8. Handle model unavailability safely.
9. Avoid logging secrets or unnecessary sensitive source.

Conceptual finding model:

    Finding
     - category
     - severity
     - confidence
     - description
     - affectedComponent
     - evidence
     - reasoning

Possible categories:
- DEPENDENCY_FAILURE
- API_CONTRACT
- DATABASE
- SECURITY
- PERFORMANCE
- CONCURRENCY
- BEHAVIORAL
- COMPATIBILITY
- TESTING_GAP
- UNKNOWN

Reuse existing domain enums if they exist.

---

# PART 11 - Keep Risk Scoring Deterministic

Do NOT let the LLM directly decide:

    risk score = 73

Instead:

    LLM
    -> InvestigationFindings
    -> DeterministicRiskEngine
    -> RiskAssessment

Extend the existing deterministic scoring model rather than replacing it.

Every risk increase should be explainable.

Example:

    critical dependency affected
    + synchronous external call
    + missing failure handling
    + production-critical component
    -> deterministic score
    -> risk level

---

# PART 12 - Evidence / RAG Integration

Use the existing EvidenceSearchPort meaningfully.

Evidence may include:
- previous incidents
- architecture documentation
- service dependencies
- operational constraints
- similar historical changes

Pass evidence to the LLM with source/context.

Do not present weak retrieval as guaranteed fact.

---

# PART 13 - Security and Prompt Safety

Before sending repository content to an external LLM:

- never include GitHub tokens
- never include API keys
- redact obvious credentials
- avoid unnecessary .env/secrets
- enforce maximum prompt size
- treat repository text/comments/docs as untrusted data, not instructions
- do not allow repository content to override system instructions

---

# PART 14 - End-to-End Tests

Create deterministic fixture cases.

## A - Low risk

Simple DTO/local formatting change.

Expected:
LOW

## B - External dependency

New synchronous external API call.

Expected:
MEDIUM/HIGH according to existing scoring rules.

## C - Security

Disable TLS verification.

Expected:
HIGH/BLOCK according to policy.

## D - Database contract

Remove/rename a database field with downstream impact.

Expected:
HIGH

## E - API contract

Remove response field/change endpoint contract.

Expected elevated risk when callers are discovered.

## F - No textual patch

Analysis degrades gracefully.

---

# PART 15 - Postman Validation

Validate the existing Postman workflow:

1. Register/use repository
2. Create/propose change
3. Create AnalysisRun
4. Wait for async processing
5. Get change
6. Inspect analysis run
7. Inspect risk
8. Inspect decision
9. Query GitHub commit status

Verify the same commit SHA is used throughout:

    requested commit SHA
      -> AnalysisRun
      -> GitHub source retrieval
      -> GitHub status

Never silently analyze a different branch/commit.

---

# PART 16 - Observability

Add structured debug logging around analysis.

Useful fields:
- analysisRunId
- tenantId
- repositoryId
- commitSha
- changedFileCount
- contextFileCount
- contextByteCount
- agentInvoked
- findingCount
- riskScore
- riskLevel

Never log:
- GITHUB_TOKEN
- API keys
- LLM credentials
- unnecessarily sensitive full source

The goal is to make adapter/bean/analysis failures diagnosable without repeated restarts and guesswork.

---

# PART 17 - Regression Verification

Run the project's existing test command, normally:

    mvn test

Verify:
- existing tests pass
- local/beta behavior remains valid where expected
- GitHub adapter is selected when token is configured
- fallback SourceControlPort does not silently override GitHub
- async dispatcher still processes all four commands
- policy evaluation remains deterministic
- GitHub status publishing remains functional

Pay special attention to Spring bean selection involving:
- SourceControlPort
- GithubSourceControlAdapter
- ConditionalOnExpression
- ConditionalOnMissingBean

---

# PART 18 - Definition of Done

## Code understanding

- [ ] Actual GitHub patch is available.
- [ ] Source can be retrieved at exact commit SHA.
- [ ] Changed Java classes are detected.
- [ ] Changed methods are detected where feasible.
- [ ] Direct dependencies are identified.
- [ ] Callers/callees are identified where feasible.
- [ ] Relevant source is bounded by configurable limits.
- [ ] Code context reaches investigation.

## AI

- [ ] Real Agent/LLM adapter exists.
- [ ] LLM receives diff + relevant source + dependency context.
- [ ] Output is structured.
- [ ] Findings include evidence and confidence.
- [ ] LLM cannot directly dictate final numeric risk.
- [ ] LLM failures are handled safely.

## Risk / decision

- [ ] DeterministicRiskEngine remains authoritative for scoring.
- [ ] PolicyEngine remains authoritative for policy decisions.
- [ ] DecisionRecord remains authoritative for final decision.

## Integration

- [ ] End-to-end PR analysis works.
- [ ] GitHub status is published.
- [ ] Status targets the correct commit.
- [ ] Analysis uses exact PR/commit snapshot.
- [ ] Postman flow succeeds.

## Quality

- [ ] Unit tests added.
- [ ] Integration tests added.
- [ ] End-to-end test added.
- [ ] No secrets committed.
- [ ] Existing tests remain green.
- [ ] Debugging information is sufficient.

---

# Recommended Implementation Order

Implement in this order:

PART 1
Inspect existing implementation
-> PART 2
GitHub patch content
-> PART 3
Commit-specific source retrieval
-> PART 4
CodeContext
-> PART 5
Changed class/method detection
-> PART 6
Direct dependency discovery
-> PART 7
Caller/callee discovery
-> PART 8
Bounded impact cone
-> PART 9
AgentContext integration
-> PART 10
Real LLM Agent
-> PART 11
Deterministic risk integration
-> PART 12
Evidence/RAG
-> PART 13
Security
-> PART 14
End-to-end tests
-> PART 15
Postman validation
-> PART 16
Observability
-> PART 17
Regression
-> PART 18
Definition of Done

---

# Instructions to OpenCode

Implement this plan incrementally.

For each part:

1. Inspect the current implementation.
2. State which files will be modified.
3. Make the smallest reasonable change.
4. Compile.
5. Run relevant tests.
6. Fix failures before moving on.
7. Do not skip tests.
8. Do not rewrite working components without evidence.
9. Do not assume a class/interface exists; inspect first.
10. If the current architecture differs from this plan, adapt while preserving the intent.
11. Keep a short implementation log of completed parts and test results.
12. Stop and report clearly if a significant architectural decision is required.

Primary objective:

Evidence-backed PR risk analysis.

The finished system should be able to explain:

"This PR is risky because these changed methods affect these dependencies, which have these possible failure modes, supported by these pieces of code/evidence."

It should not merely produce:

"Risk = 40."
