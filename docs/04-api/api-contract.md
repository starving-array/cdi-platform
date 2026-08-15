# V1 REST API Contract

This document defines the REST API contract for the Engineering Change Decision Intelligence platform. The API exposes application use cases using resource-oriented URLs, JSON payloads, and strict versioning (`/api/v1`). It explicitly hides internal database structures, specific LLM providers, and GitHub SDK models to ensure future scalability and portability.

## 1. API Conventions

- **Base Path**: `/api/v1`
- **Format**: JSON requests and responses.
- **Asynchronous Operations**: Return `202 Accepted` with a tracking URL (e.g., polling the analysis run).
- **Idempotency**: Mutating endpoints (`POST`, `PUT`) require an `Idempotency-Key` header where specified.
- **Tenant Context**: Inferred via the Authorization token, but cross-tenant actions require the `organizationId` in the path.

---

## 2. Resource Endpoints

### 2.1 Organizations & Repositories
*Minimal endpoints for basic setup.*

- `POST /organizations`
- `GET /organizations/{organizationId}`
- `POST /organizations/{organizationId}/repositories`
  - **Body**: `{"name": "core-backend", "provider": "GITHUB", "externalId": "123456", "defaultBranch": "main"}`
- `GET /repositories/{repositoryId}`

### 2.2 Services (System Context)
- `POST /organizations/{organizationId}/services`
  - **Body**: `{"name": "payment-service", "criticalityTier": "TIER_0", "owner": "team-payments"}`
- `GET /services/{serviceId}`

### 2.3 Policies
- `POST /organizations/{organizationId}/policies`
- `GET /organizations/{organizationId}/policies`
- `GET /policies/{policyId}/versions`

---

## 3. Core Analysis APIs

### 3.1 Receive a Change
Intakes a new software change from an SCM provider (or manually).

**`POST /changes`**
- **Headers**: `Idempotency-Key: <hash>`
- **Request Body**:
```json
{
  "repositoryId": "repo-uuid-1234",
  "providerChangeId": "42",
  "commitSha": "abc123def456",
  "title": "Update payment gateway logic",
  "author": "engineer-1"
}
```
- **Response**: `201 Created` or `200 OK` (if idempotent match)
```json
{
  "changeId": "change-uuid-5678",
  "status": "DETECTED"
}
```

### 3.2 Retrieve a Change
Retrieves the logical PR and its analysis history.

**`GET /changes/{changeId}`**
- **Response**: `200 OK`
```json
{
  "changeId": "change-uuid-5678",
  "repositoryId": "repo-uuid-1234",
  "providerChangeId": "42",
  "currentCommitSha": "abc123def456"
}
```

**`GET /changes/{changeId}/analyses`**
- **Response**: `200 OK` - Lists all `AnalysisRun` objects for this change (representing each commit snapshot).

### 3.3 Trigger Analysis (Asynchronous)
Triggers the full analysis workflow for a specific commit snapshot.

**`POST /changes/{changeId}/analyze`**
- **Headers**: `Idempotency-Key: <commitSha>`
- **Request Body**:
```json
{
  "commitSha": "abc123def456"
}
```
- **Response**: `202 Accepted`
```json
{
  "analysisId": "analysis-uuid-9012",
  "status": "QUEUED",
  "statusUrl": "/api/v1/analysis-runs/analysis-uuid-9012"
}
```
- **Concurrency**: If an analysis is already running for this exact commit, returns `200 OK` with the existing `analysisId`. If a newer commit is submitted while an older one is running, the older one is marked `SUPERSEDED` by the background worker.

### 3.4 Retrieve Analysis Run
Retrieves the aggregated view of a specific commit analysis.

**`GET /analysis-runs/{analysisId}`**
- **Response**: `200 OK`
```json
{
  "analysisId": "analysis-uuid-9012",
  "changeId": "change-uuid-5678",
  "commitSha": "abc123def456",
  "status": "COMPLETED",
  "createdAt": "2026-08-11T08:00:00Z",
  "superseded": false,
  "risk": {
    "score": "HIGH",
    "factors": [
      {"description": "Touches 5 files in core payment module", "source": "DETERMINISTIC"}
    ]
  },
  "investigation": {
    "status": "COMPLETED",
    "findings": [
      {
        "text": "The change alters Kafka event schemas without providing fallback compatibility.",
        "evidenceCitations": ["evidence-uuid-1111"]
      }
    ]
  },
  "evidence": [
    {
      "evidenceId": "evidence-uuid-1111",
      "sourceType": "INCIDENT",
      "sourceReference": "INC-899",
      "summary": "Previous schema change caused consumer failure."
    }
  ],
  "decision": {
    "outcome": "REVIEW_REQUIRED",
    "policyVersionId": "policy-ver-uuid-3333",
    "reasons": ["Tier 0 service modified with HIGH risk score."],
    "generatedAt": "2026-08-11T08:05:00Z"
  }
}
```
- **Note**: This endpoint aggregates Risk, Investigation, Evidence, and Decision to avoid excessive API fragmentation (N+1 queries for the UI). The schema clearly separates Risk from Decision. Private LLM internals (chains, prompts) are explicitly omitted.

---

## 4. Error Model

All errors follow a standard JSON structure. HTTP Status codes map broadly to these categories (e.g., 400 for Validation, 404 for Not Found, 409 for Conflict).

```json
{
  "code": "ANALYSIS_SUPERSEDED",
  "message": "This analysis run has been superseded by a newer commit.",
  "details": {
    "newerCommitSha": "xyz987"
  },
  "correlationId": "corr-uuid-1234"
}
```

### Common Error Codes:
- `VALIDATION_FAILED`: Bad request body.
- `UNAUTHORIZED`: Missing or invalid token.
- `FORBIDDEN`: Actor lacks permission for the resource.
- `RESOURCE_NOT_FOUND`: Change, Analysis, or Repo does not exist.
- `ANALYSIS_SUPERSEDED`: Attempted action on an obsolete analysis run.
- `EVIDENCE_UNAVAILABLE`: Graceful degradation indicator (system proceeded, but RAG failed).
- `SYSTEM_FAILURE`: Unrecoverable internal error.

---

## 5. Idempotency & Concurrency

- **Idempotency**: Required on `POST /changes` and `POST /changes/{changeId}/analyze`. Safe to retry blindly on network failures.
- **Concurrency**: 
  - `409 Conflict`: Returned if an engineer attempts to manually override a decision that has already been superseded by a new commit.
  - No locking required for reads (`GET`).

---

## 6. Authorization Model (Conceptual)

- **Organization Admin**: Has `POST/PUT` access to `/organizations`, `/repositories`, `/services`, and `/policies`. Can override decisions.
- **System Worker**: (Internal OAuth Client) Has write access to `analysis-runs` to update state asynchronously.
- **Engineer**: Has `GET` access to all resources. Can `POST` to `/changes/{changeId}/analyze` to manually request re-runs.

---

## 7. Example Workflow (Client Side)

1. **Intake**: GitHub Webhook fires. API translates it to `POST /changes` (idempotent).
2. **Trigger**: API calls `POST /changes/{id}/analyze`. Gets `202 Accepted` + `analysisId`.
3. **Poll**: Client polls `GET /analysis-runs/{analysisId}`.
   - 0s: `status: QUEUED`
   - 2s: `status: RUNNING`, `risk` is `null`.
   - 10s: `status: RUNNING`, `risk` is populated (`HIGH`), `investigation` is `RUNNING`.
   - 45s: `status: COMPLETED`, `investigation` findings populated, `decision` outcome (`REVIEW_REQUIRED`) populated.
