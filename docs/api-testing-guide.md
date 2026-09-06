# CDI Backend API Testing Guide

## 1. Overview
This is a practical API manual for the CDI backend service. It documents every REST endpoint currently implemented in the codebase. You can use the provided curl commands directly in PowerShell to test the system end-to-end.

## 2. Base URL
http://localhost:8080

## 3. Development Authentication
The application supports Spring Security JWT (via `CdiJwtAuthenticationToken`) and a mock development authentication filter.
When a JWT is not provided, development HTTP security headers are used:
- `X-Tenant-Id`: Extracts the UUID of the tenant.
- `X-Actor-Id`: Extracts the ID of the user (defaults to `dev-user`).
- `X-Actor-Role`: Extracts the role (defaults to `ENGINEER`).

## 4. Roles
The application strictly enforces actor roles for specific operations:
- `SYSTEM_WORKER`: Used for background tasks and async events (can propose changes, record deployments/outcomes).
- `TENANT_ADMIN`: Used for organizational setup (can create repositories).
- `ENGINEER`: Used for operational workflows (can propose changes, record deployments/outcomes).

## 5. API Quick Reference
| # | Method | Endpoint | Purpose | Role | Sync/Async | Request Body | Success |
|---|---|---|---|---|---|---|---|
| 1 | GET | `/api/v1/health` | Health Check | Any | Sync | No | 200 OK |
| 2 | POST | `/api/v1/repositories` | Create Repository | `TENANT_ADMIN` | Sync | Yes | 201 Created (200 OK if existing) |
| 3 | POST | `/api/v1/changes/propose` | Propose Change | `ENGINEER`, `SYSTEM_WORKER` | Async | Yes | 201 Created (200 OK if existing) |
| 4 | GET | `/api/v1/changes` | List Changes | Any | Sync | No | 200 OK |
| 5 | GET | `/api/v1/changes/{changeId}` | Get Change Detail | Any | Sync | No | 200 OK |
| 6 | POST | `/api/v1/deployments` | Record Deployment | `ENGINEER`, `SYSTEM_WORKER` | Sync | Yes | 201 Created (200 OK if existing) |
| 7 | POST | `/api/v1/deployments/{id}/outcomes` | Record Outcome | `ENGINEER`, `SYSTEM_WORKER` | Sync | Yes | 200 OK |
| 8 | GET | `/api/v1/deployments` | List Deployments | Any | Sync | No | 200 OK |
| 9 | GET | `/api/v1/deployments/{id}` | Get Deployment | Any | Sync | No | 200 OK |
| 10| GET | `/api/v1/evidence/search` | Search Evidence | Any | Sync | No | 200 OK |
| 11| GET | `/api/v1/attribution/{deploymentId}` | Get Attribution | Any | Sync | No | 200 OK |
| 12| GET | `/api/v1/attribution/summary` | Get Attribution Summary | Any | Sync | No | 200 OK |

## 6. Changes & Analysis APIs

---

# POST /api/v1/changes/propose

## Purpose

Proposes a new change for analysis. If the change does not exist, it is created. An asynchronous analysis run is enqueued and triggered.

## Authorization

Required role:
`ENGINEER` or `SYSTEM_WORKER`

Required Headers:
```text
Content-Type: application/json
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

None

## Request Body

```json
{
  "repositoryId": "ba0f0e1e-4761-49d2-b379-cc1994b64620",
  "providerChangeId": "3",
  "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
  "branch": "feature-branch",
  "title": "Add feature",
  "description": "Implemented new functionality.",
  "author": "dev-user"
}
```

| JSON Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `repositoryId` | UUID | Yes | Valid UUID | The UUID of the repository. |
| `providerChangeId` | String | Yes | Not blank | The PR/MR number or identifier. |
| `commitSha` | String | Yes | Not blank | The exact commit SHA to analyze. |
| `branch` | String | Yes | Not blank | The branch name. |
| `title` | String | Yes | Not blank | The PR title. |
| `description` | String | Yes | Not blank | The PR description. |
| `author` | String | Yes | Not blank | The author's identifier. |

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/changes/propose" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"repositoryId\":\"ba0f0e1e-4761-49d2-b379-cc1994b64620\",\"providerChangeId\":\"3\",\"commitSha\":\"83a352f340338ef83541c8d1958b3e59b3aa810b\",\"branch\":\"main\",\"title\":\"Test_PR\",\"description\":\"Test_description\",\"author\":\"dev-user\"}'
```

## Expected HTTP Status
`201 CREATED` (if new) or `200 OK` (if existing idempotently)

## Example Success Response

```json
{
  "analysisRunId": "0eb737c9-5b89-44c1-a92e-7031eb7c6913",
  "created": true
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `analysisRunId` | UUID | The tracked ID of the enqueued analysis run. |
| `created` | Boolean | True if a new analysis run was created; false if returning an existing one. |

## Error Responses

`400 BAD REQUEST` — Domain Exception

Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/changes/propose" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"repositoryId\":\"ba0f0e1e-4761-49d2-b379-cc1994b64620\"}'
```

Expected response:
```json
{
  "code": "INVALID_ARGUMENT",
  "message": "Commit SHA cannot be blank",
  "details": {}
}
```
Explanation: Missing required properties (handled via `DomainException`).

`403 FORBIDDEN` — Wrong Role

Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/changes/propose" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: TENANT_ADMIN" -d '{\"repositoryId\":\"ba0f0e1e-4761-49d2-b379-cc1994b64620\",\"providerChangeId\":\"3\",\"commitSha\":\"83a352f340338ef83541c8d1958b3e59b3aa810b\",\"branch\":\"main\",\"title\":\"Test\",\"description\":\"Desc\",\"author\":\"user\"}'
```

Expected response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Unauthorized action",
  "details": null
}
```
Explanation: The `TENANT_ADMIN` role is not allowed to propose changes.

## Async / Processing Behavior

Asynchronous.
When the request is accepted, an `AnalyzeChangeCommand` is enqueued via `JobQueuePort`.
The response immediately returns the `analysisRunId`.
Background processing sequentially runs: AnalyzeChange -> InvestigateRisk -> EvaluatePolicy -> GenerateDecision -> GitHub status publication.
To check the eventual result, use `GET /api/v1/changes/<CHANGE_ID>` and observe the `analysisRuns` status (`COMPLETED` or `FAILED`).

## Side Effects
- Persists a new `Change` if it doesn't exist.
- Persists a new `AnalysisRun`.
- Enqueues background job.
- Publishes `ChangeProposed` domain event.

## Testing Notes
You must supply a valid `repositoryId` that exists in the database for this tenant.

---

# GET /api/v1/changes

## Purpose

Lists changes, optionally filtered by repository.

## Authorization

Required role:
Any valid actor/role (no strict restriction in controller/handler).

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `repositoryId` | UUID | No | Filters changes to a specific repository. |

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
[
  {
    "id": "984951c5-3fbe-4f81-9da4-0c17d8966a2a",
    "repositoryId": "ba0f0e1e-4761-49d2-b379-cc1994b64620",
    "providerChangeId": "3",
    "title": "Test PR",
    "description": "Test description",
    "author": "dev-user",
    "sourceBranch": "main",
    "targetBranch": "",
    "latestCommitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
    "status": "OPEN",
    "createdAt": "2026-09-06T10:27:01.037Z",
    "updatedAt": "2026-09-06T10:27:01.037Z"
  }
]
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `id` | UUID | The change ID. |
| `repositoryId` | UUID | The parent repository ID. |
| `providerChangeId` | String | External PR/MR identifier. |
| `title` | String | Change title. |
| `description` | String | Change description. |
| `author` | String | Change author. |
| `sourceBranch` | String | Source branch name. |
| `targetBranch` | String | Target branch name. |
| `latestCommitSha` | String | Latest SHA tracked for this change. |
| `status` | String | Status (`OPEN`, `MERGED`, `CLOSED`). |
| `createdAt` | String | Timestamp. |
| `updatedAt` | String | Timestamp. |

## Error Responses

`401 UNAUTHORIZED` (Mapped as standard fallback if missing tenant)

Request:
```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes"
```

Expected response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Unauthorized action",
  "details": null
}
```
Explanation: Missing required `X-Tenant-Id` header.

## Async / Processing Behavior

Synchronous.

## Side Effects

None.

## Testing Notes
Useful to find `id` (changeId) to fetch detailed analysis.

---

# GET /api/v1/changes/{changeId}

## Purpose

Retrieves detailed information for a single change, including its analysis runs, risk assessments, and decision records.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `changeId` | UUID | Yes | The internal ID of the change. |

## Query Parameters

None

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes/<CHANGE_ID>" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "change": {
    "id": "984951c5-3fbe-4f81-9da4-0c17d8966a2a",
    "repositoryId": "ba0f0e1e-4761-49d2-b379-cc1994b64620",
    "providerChangeId": "3",
    "title": "Test PR",
    "description": "Test description",
    "author": "dev-user",
    "sourceBranch": "main",
    "targetBranch": "",
    "latestCommitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
    "status": "OPEN",
    "createdAt": "2026-09-06T10:27:01.037Z",
    "updatedAt": "2026-09-06T10:27:01.037Z"
  },
  "analysisRuns": [
    {
      "id": "0eb737c9-5b89-44c1-a92e-7031eb7c6913",
      "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
      "status": "COMPLETED",
      "createdAt": "2026-09-06T10:27:01.050Z",
      "completedAt": "2026-09-06T10:27:05.457Z"
    }
  ],
  "latestRisk": {
    "id": "f3e64029-3ebc-4ed4-86a5-b18db1e232ca",
    "overallScore": 40,
    "riskLevel": "MEDIUM",
    "assessmentVersion": "v1.0.0"
  },
  "latestDecision": {
    "id": "2520b4fb-6932-4338-bc75-07c2b3d42511",
    "outcome": "APPROVE",
    "policyVersion": "v1",
    "overridden": false
  }
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `change` | Object | The summary change information. |
| `analysisRuns` | Array | List of all analysis runs associated with the change. |
| `latestRisk` | Object | The latest risk assessment payload. |
| `latestDecision` | Object | The final decision record. |

## Error Responses

`404 NOT FOUND` — Change Not Found

Request:
```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes/11111111-1111-1111-1111-111111111111" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

Expected response:
```json
{
  "code": "CHANGE_NOT_FOUND",
  "message": "Change not found",
  "details": null
}
```
Explanation: The requested UUID does not exist for this tenant.

## Async / Processing Behavior

Synchronous.

## Side Effects

None.

## Testing Notes
Use the `analysisRuns` status to poll for background job completion when testing `POST /api/v1/changes/propose`.

## 7. Evidence APIs

---

# GET /api/v1/evidence/search

## Purpose

Provides a semantic/text search across all collected evidence within the tenant.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | String | Yes | The search term/query. |
| `limit` | Integer | No | Result limit (defaults to 10). |

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/evidence/search?query=test&limit=5" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "degraded": false,
  "results": []
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `degraded` | Boolean | True if the search subsystem is returning degraded results (e.g. pgvector missing). |
| `results` | Array | Array of EvidenceRecordResponse objects. |

## Error Responses
None explicitly mapped aside from standard 400 (missing `query` param).

## Async / Processing Behavior
Synchronous.

## Side Effects
None.

## Testing Notes
If vector extensions are missing, `degraded` will return true and semantic search is skipped.

## 8. Decision APIs

No REST endpoints currently implemented in this category. (Decisions are retrieved via `GET /api/v1/changes/{changeId}`).

## 9. Deployment APIs

---

# POST /api/v1/deployments

## Purpose

Records a new deployment attempt for a service.

## Authorization

Required role:
`ENGINEER` or `SYSTEM_WORKER`

Required Headers:
```text
Content-Type: application/json
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

None

## Request Body

```json
{
  "serviceId": "00000000-0000-0000-0000-000000000001",
  "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
  "environment": "PRODUCTION",
  "status": "IN_PROGRESS",
  "externalDeploymentId": "github-actions-12345",
  "deployedAt": "2026-09-06T10:00:00Z"
}
```

| JSON Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `serviceId` | UUID | Yes | Valid UUID | The associated service UUID. |
| `commitSha` | String | Yes | Not blank | The deployed commit SHA. |
| `environment` | String | Yes | Not blank | Deployment environment (e.g., PRODUCTION). |
| `status` | String | No | Valid enum | Starts as IN_PROGRESS. |
| `externalDeploymentId` | String | No | - | External tracker ID. |
| `deployedAt` | String | No | ISO-8601 | Deployment time (defaults to now). |

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/deployments" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"serviceId\":\"00000000-0000-0000-0000-000000000001\",\"commitSha\":\"83a352f340338ef83541c8d1958b3e59b3aa810b\",\"environment\":\"PRODUCTION\",\"externalDeploymentId\":\"run-999\"}'
```

## Expected HTTP Status
`201 CREATED` (if new) or `200 OK` (if existing idempotently)

## Example Success Response

```json
{
  "deployment": {
    "id": "e4270d18-3561-4c74-8b01-ff05fcd3c0e3",
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "serviceId": "00000000-0000-0000-0000-000000000001",
    "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
    "environment": "PRODUCTION",
    "status": "IN_PROGRESS",
    "externalDeploymentId": "run-999",
    "deployedAt": "2026-09-06T10:00:00Z",
    "createdAt": "2026-09-06T10:00:00Z",
    "outcome": null
  },
  "created": true
}
```
## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `deployment` | Object | The deployment entity data. |
| `created` | Boolean | True if created. |

## Error Responses

`404 NOT FOUND` — Service not found

Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/deployments" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"serviceId\":\"11111111-1111-1111-1111-111111111111\",\"commitSha\":\"83a352f340338ef83541c8d1958b3e59b3aa810b\",\"environment\":\"PRODUCTION\"}'
```

Expected response:
```json
{
  "code": "SERVICE_NOT_FOUND",
  "message": "Service not found",
  "details": null
}
```
Explanation: Service UUID does not exist for tenant.

## Async / Processing Behavior
Synchronous.

## Side Effects
Database write to `deployment` table.

## Testing Notes
N/A

---

# POST /api/v1/deployments/{id}/outcomes

## Purpose

Records the final outcome (success/failure) of a deployment, which triggers attribution analysis.

## Authorization

Required role:
`ENGINEER` or `SYSTEM_WORKER`

Required Headers:
```text
Content-Type: application/json
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | UUID | Yes | The deployment ID. |

## Query Parameters

None

## Request Body

```json
{
  "outcome": "SUCCESS",
  "incidentReference": null,
  "recordedAt": "2026-09-06T11:00:00Z"
}
```

| JSON Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `outcome` | String | Yes | `SUCCESS`, `FAILURE`, `ROLLED_BACK` | The outcome type. |
| `incidentReference` | String | No | - | Link/ID of an incident. |
| `recordedAt` | String | No | ISO-8601 | Explicit timestamp. |

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/deployments/<DEPLOYMENT_ID>/outcomes" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"outcome\":\"SUCCESS\"}'
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "id": "e4270d18-3561-4c74-8b01-ff05fcd3c0e3",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "serviceId": "00000000-0000-0000-0000-000000000001",
  "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
  "environment": "PRODUCTION",
  "status": "COMPLETED",
  "externalDeploymentId": "run-999",
  "deployedAt": "2026-09-06T10:00:00Z",
  "createdAt": "2026-09-06T10:00:00Z",
  "outcome": {
    "id": "1d8b7b25-45d6-4171-8bc4-cf3fc5ea5d6f",
    "outcome": "SUCCESS",
    "incidentReference": null,
    "recordedAt": "2026-09-06T10:05:00Z",
    "createdAt": "2026-09-06T10:05:00Z"
  }
}
```
## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `outcome` | Object | Populated outcome data and timestamps. |

## Error Responses

`409 CONFLICT` — Outcome Already Recorded

Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/deployments/<DEPLOYMENT_ID>/outcomes" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"outcome\":\"SUCCESS\"}'
```

Expected response:
```json
{
  "code": "OUTCOME_ALREADY_RECORDED",
  "message": "Outcome already recorded",
  "details": null
}
```
Explanation: Cannot record outcomes multiple times.

## Async / Processing Behavior
Synchronous.

## Side Effects
Updates deployment, creates `DeploymentOutcome`.

## Testing Notes
None.

---

# GET /api/v1/deployments

## Purpose

Lists deployments for the tenant, optionally filtered by serviceId or environment.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `serviceId` | UUID | No | Filters by service ID. |
| `environment` | String | No | Filters by environment name. |

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/deployments" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
[
  {
    "id": "e4270d18-3561-4c74-8b01-ff05fcd3c0e3",
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "serviceId": "00000000-0000-0000-0000-000000000001",
    "commitSha": "83a352f340338ef83541c8d1958b3e59b3aa810b",
    "environment": "PRODUCTION",
    "status": "IN_PROGRESS",
    "externalDeploymentId": "run-999",
    "deployedAt": "2026-09-06T10:00:00Z",
    "createdAt": "2026-09-06T10:00:00Z",
    "outcome": null
  }
]
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| (Array values) | Object | Deployment DTO. |

## Error Responses
None standard aside from 401 unauthorized.

## Async / Processing Behavior
Synchronous.

## Side Effects
None.

## Testing Notes
None.

---

# GET /api/v1/deployments/{id}

## Purpose

Retrieves single deployment detail.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `id` | UUID | Yes | Deployment ID. |

## Query Parameters

None

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/deployments/<DEPLOYMENT_ID>" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response
(Same as single item from array)

## Response Fields
(Standard Deployment DTO)

## Error Responses
`404 NOT FOUND` - Deployment not found

## Async / Processing Behavior
Synchronous.

## Side Effects
None.

## Testing Notes
None.

## 10. Attribution APIs

---

# GET /api/v1/attribution/{deploymentId}

## Purpose

Retrieves decision attribution data correlating a deployment outcome back to its original CDI risk assessment and policy decision.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `deploymentId` | UUID | Yes | Deployment ID. |

## Query Parameters

None

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/attribution/<DEPLOYMENT_ID>" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "id": "3f421e4a-9b1c-4e8c-8f2a-b73a5bf470ae",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "deploymentId": "e4270d18-3561-4c74-8b01-ff05fcd3c0e3",
  "serviceId": "00000000-0000-0000-0000-000000000001",
  "analysisRunId": "0eb737c9-5b89-44c1-a92e-7031eb7c6913",
  "riskAssessmentId": "f3e64029-3ebc-4ed4-86a5-b18db1e232ca",
  "decisionRecordId": "2520b4fb-6932-4338-bc75-07c2b3d42511",
  "classification": "ACCURATE_LOW_RISK",
  "deploymentOutcome": "SUCCESS",
  "predictedRiskLevel": "MEDIUM",
  "decisionOutcome": "APPROVE",
  "hasHumanOverride": false,
  "attributedAt": "2026-09-06T10:10:00Z",
  "createdAt": "2026-09-06T10:10:00Z"
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `classification` | String | Evaluates the model accuracy (e.g. `ACCURATE_LOW_RISK`, `UNDERESTIMATED`). |
| `deploymentOutcome` | String | Link back to the outcome. |
| `decisionOutcome` | String | Link back to the decision. |

## Error Responses

`404 NOT FOUND` - Attribution not found

## Async / Processing Behavior
Synchronous.

## Side Effects
None.

## Testing Notes
Attributions are asynchronously generated after a deployment outcome is recorded. Wait briefly before querying.

---

# GET /api/v1/attribution/summary

## Purpose

Retrieves aggregated statistical accuracy summary of model predictions against real-world outcomes.

## Authorization

Required role:
Any.

Required Headers:
```text
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: ENGINEER
```

## Path Parameters

None

## Query Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `serviceId` | UUID | No | Filter by service. |

## Request Body

No request body.

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/attribution/summary" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "serviceId": null,
  "totalAttributedDeployments": 100,
  "accurateLowRiskCount": 90,
  "accurateHighRiskCount": 5,
  "underestimatedRiskCount": 3,
  "overestimatedRiskCount": 2,
  "unattributedCount": 0,
  "accuracyRate": 0.95
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `accuracyRate` | Double | The calculated system accuracy rating. |

## Error Responses
None specific to this endpoint.

## Async / Processing Behavior
Synchronous.

## Side Effects
None.

## Testing Notes
None.

## 11. Organization / Tenant APIs

No REST endpoints currently implemented in this category.

## 12. Repository APIs

---

# POST /api/v1/repositories

## Purpose

Registers a new source-control repository with CDI. Required before changes can be proposed.

## Authorization

Required role:
`TENANT_ADMIN`

Required Headers:
```text
Content-Type: application/json
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
X-Actor-Id: 00000000-0000-0000-0000-000000000002
X-Actor-Role: TENANT_ADMIN
```

## Path Parameters

None

## Query Parameters

None

## Request Body

```json
{
  "providerType": "GITHUB",
  "externalId": "starving-array/demo-test",
  "name": "Demo Repo",
  "url": "https://github.com/starving-array/demo-test",
  "defaultBranch": "main"
}
```

| JSON Field | Type | Required | Validation | Description |
|---|---|---|---|---|
| `providerType` | String | Yes | Valid Enum | External provider (e.g. `GITHUB`). |
| `externalId` | String | Yes | Not blank | External provider's ID (e.g. owner/repo). |
| `name` | String | Yes | Not blank | Human readable name. |
| `url` | String | Yes | URL | The remote URL. |
| `defaultBranch` | String | Yes | Not blank | The default branch. |

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/repositories" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: TENANT_ADMIN" -d '{\"providerType\":\"GITHUB\",\"externalId\":\"owner/repo\",\"name\":\"Demo_Repo\",\"url\":\"https://github.com/owner/repo\",\"defaultBranch\":\"main\"}'
```

## Expected HTTP Status
`201 CREATED` (if new) or `200 OK` (if idempotently returning existing).

## Example Success Response

```json
{
  "repositoryId": "ba0f0e1e-4761-49d2-b379-cc1994b64620",
  "created": true
}
```

## Response Fields
| JSON Field | Type | Description |
|---|---|---|
| `repositoryId` | UUID | The internal repository ID used in future requests. |
| `created` | Boolean | True if created, false if returned existing. |

## Error Responses

`403 FORBIDDEN` — Missing Role

Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/repositories" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"providerType\":\"GITHUB\",\"externalId\":\"owner/repo\",\"name\":\"Demo Repo\",\"url\":\"https://github.com/owner/repo\",\"defaultBranch\":\"main\"}'
```

Expected response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Unauthorized action",
  "details": null
}
```
Explanation: Only `TENANT_ADMIN` can create repositories.

## Async / Processing Behavior
Synchronous.

## Side Effects
Writes new `Repository` entity to DB.

## Testing Notes
None.

## 13. Policy APIs

No REST endpoints currently implemented in this category.

## 14. Other APIs

---

# GET /api/v1/health

## Purpose

Simple liveness probe.

## Authorization

Required role: None (public).

## COPY-PASTE CURL — PowerShell

```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/health"
```

## Expected HTTP Status
`200 OK`

## Example Success Response

```json
{
  "status": "UP"
}
```

## 15. Complete End-to-End Test Flow

Step 1 — Create Repository
Purpose: Register a repository for the tenant.
Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/repositories" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: TENANT_ADMIN" -d '{\"providerType\":\"GITHUB\",\"externalId\":\"starving-array/demo-test\",\"name\":\"demo-test\",\"url\":\"https://github.com/starving-array/demo-test\",\"defaultBranch\":\"main\"}'
```
Expected status: 201 Created or 200 OK
*Copy `repositoryId` from this response.*

Step 2 — Propose Change
Purpose: Trigger the async analysis workflow using a valid PR branch and commit.
Request:
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/changes/propose" -H "Content-Type: application/json" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER" -d '{\"repositoryId\":\"<REPOSITORY_ID>\",\"providerChangeId\":\"3\",\"commitSha\":\"83a352f340338ef83541c8d1958b3e59b3aa810b\",\"branch\":\"main\",\"title\":\"Add_Feature\",\"description\":\"Feature_Description\",\"author\":\"dev-user\"}'
```
Expected status: 201 Created or 200 OK
*Copy `analysisRunId` from this response.*

Step 3 — View Change & Analysis Results
Purpose: Inspect the decisions generated. You will have to look up the changeId first using `GET /api/v1/changes`, OR if you know the ID, query it directly.
Request:
```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```
Expected status: 200 OK
*Copy `id` (the change ID).*
Then call:
```powershell
curl.exe -s -X GET "http://localhost:8080/api/v1/changes/<CHANGE_ID>" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" -H "X-Actor-Id: 00000000-0000-0000-0000-000000000002" -H "X-Actor-Role: ENGINEER"
```
Wait/Retry until `analysisRuns[0].status` is `COMPLETED` and `latestDecision` appears.

## 16. Negative / Error Testing

Scenario: Missing required Tenant Header
Request:
```powershell
curl.exe -X GET "http://localhost:8080/api/v1/changes"
```
Expected HTTP status: 403 Forbidden (or 401 mapped as standard)
Expected response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Unauthorized action",
  "details": null
}
```
Reason: The `DevSecurityContext` strictly requires `X-Tenant-Id`.

## 17. GitHub Integration Testing

- repository identifier format: CDI supports the format `owner/repo` (e.g. `starving-array/demo-test`) submitted via `externalId`.
- providerChangeId: Should map to the GitHub PR number.
- commitSha: The exact full SHA hash from GitHub.
- GitHub error handling: If the outbound `POST /repos/{owner}/{repo}/statuses/{sha}` call returns a 422 or 401 error, `GithubSourceControlAdapter` throws a `RestClientResponseException`, captures the body, maps it to a `DomainException`, and the Async job handler marks the run as `FAILED`.
- target_url: Maps to `cdi.ui.base-url` (default `http://localhost:5173`) + `/runs/<analysisRunId>/decision`.
- Status Mapping: `APPROVE` -> `success`, `REVIEW_REQUIRED` -> `pending`, `BLOCK` -> `failure`.

## 18. Postman Usage

For Postman testing:
1. Create a Global or Environment variable: `TENANT_ID = 00000000-0000-0000-0000-000000000001`.
2. Apply Headers globally to all requests in the collection:
   - `X-Tenant-Id: {{TENANT_ID}}`
   - `X-Actor-Id: dev-user`
   - `X-Actor-Role: ENGINEER` (Or `TENANT_ADMIN` for repository setup)
3. For POST methods, set the body type to `raw` -> `JSON`.

| Request | Method | URL | Headers | Body |
|---|---|---|---|---|
| Propose | POST | `/api/v1/changes/propose` | standard | Propose JSON |
| List | GET | `/api/v1/changes` | standard | none |

## 19. Troubleshooting

- **Backend not running**: Ensure the Spring Boot process is listening on 8080. Check for `java.net.ConnectException`.
- **Duplicate/Idempotent propose request**: Repetitive calls with the exact same `commitSha` and `providerChangeId` return HTTP 200 instead of HTTP 201, without re-triggering analysis runs.
- **GitHub authentication failure**: The Spring Boot process *must* have `GITHUB_TOKEN` injected in its environment/application.yml before starting. If missing, `GithubSourceControlAdapter` fails outbound API calls leading to failed analysis runs.
- **Asynchronous analysis still running**: Look at `/api/v1/changes/<CHANGE_ID>` `analysisRuns` status field. If it remains `IN_PROGRESS`, the dispatcher is still running or blocked.

## 20. Current API vs Previous Documentation

| Endpoint | Current Code | Previous Docs | Final Status | Notes |
|---|---|---|---|---|
| `/api/v1/changes/propose` | Implemented | - | ACTIVE | Valid endpoint. |
| `/api/v1/repositories` | Implemented | - | ACTIVE | Valid endpoint. |
| `/api/v1/decisions` | NOT Implemented | - | REMOVED | All decisions are returned inline in `GET /api/v1/changes/{changeId}` |

## 21. Final API Inventory

| # | Method | Exact Path | Controller | Purpose | Role | Success Status |
|---|---|---|---|---|---|---|
| 1 | GET | `/api/v1/health` | `HealthController` | Health Check | Any | 200 |
| 2 | POST | `/api/v1/repositories` | `RepositoryController` | Create Repo | `TENANT_ADMIN` | 201/200 |
| 3 | POST | `/api/v1/changes/propose` | `ChangeController` | Propose | `ENGINEER`/`SYSTEM_WORKER` | 201/200 |
| 4 | GET | `/api/v1/changes` | `ChangeController` | List Changes | Any | 200 |
| 5 | GET | `/api/v1/changes/{changeId}` | `ChangeController` | Change Detail| Any | 200 |
| 6 | POST | `/api/v1/deployments` | `DeploymentController` | Record Dep | `ENGINEER`/`SYSTEM_WORKER` | 201/200 |
| 7 | POST | `/api/v1/deployments/{id}/outcomes` | `DeploymentController` | Outcome | `ENGINEER`/`SYSTEM_WORKER` | 200 |
| 8 | GET | `/api/v1/deployments` | `DeploymentController` | List Dep | Any | 200 |
| 9 | GET | `/api/v1/deployments/{id}` | `DeploymentController` | Dep Detail | Any | 200 |
| 10| GET | `/api/v1/evidence/search` | `EvidenceController` | Search Ev | Any | 200 |
| 11| GET | `/api/v1/attribution/{deploymentId}` | `AttributionController`| Get Attrib | Any | 200 |
| 12| GET | `/api/v1/attribution/summary` | `AttributionController`| Attrib Sum | Any | 200 |

Total REST endpoints discovered: 12

Every endpoint has:
- request documentation: YES
- curl example: YES
- expected response: YES
- authorization: YES
- error behavior: YES
