# REST API Developer Guide

---

## 1. Authentication & Security Headers

During development and staging, requests use headers:
- `X-Tenant-Id`: UUID identifying the tenant context.
- `X-Actor-Id`: String identifying the authenticated actor.
- `X-Actor-Role`: One of `ENGINEER`, `TENANT_ADMIN`, `SYSTEM_WORKER`.

---

## 2. Core Endpoints Summary

### Changes & Analysis
- `POST /api/v1/changes/propose`: Ingest a change proposal.
- `POST /api/v1/changes/{changeId}/analyses`: Trigger analysis and evaluation.
- `GET /api/v1/changes/{changeId}`: Retrieve change analysis run history, risk score, and decision verdict.
- `GET /api/v1/changes`: List changes with optional repository or author filtering.

### Semantic Evidence
- `POST /api/v1/evidence/search`: Execute vector similarity search against tenant evidence memory.

### Governance & Human Oversight
- `POST /api/v1/decisions/{decisionId}/override`: Override a decision verdict with mandatory justification (`APPROVE` or `BLOCK`).

### Deployment Tracking
- `POST /api/v1/deployments`: Record a software deployment event.
- `POST /api/v1/deployments/{id}/outcomes`: Attach a release outcome (`SUCCESS`, `FAILURE`, `INCIDENT`, `ROLLED_BACK`).
- `GET /api/v1/deployments`: List deployments with optional service or environment filter.
- `GET /api/v1/deployments/{id}`: Fetch deployment details.

### Decision Attribution & Feedback
- `GET /api/v1/attribution/{deploymentId}`: Fetch observational attribution linking outcome to risk prediction.
- `GET /api/v1/attribution/summary?serviceId={serviceId}`: Fetch service or tenant decision accuracy statistics.