# Change Decision Intelligence (CDI) Platform

An enterprise-grade, multi-tenant platform designed to assess, govern, and evaluate software engineering changes before they impact production environments.

CDI combines deterministic risk analysis, semantic evidence retrieval, policy enforcement, human oversight, deployment tracking, and observational attribution to give engineering teams confidence in every release.

---

## 1. Overview & Problem Statement

Modern software engineering organizations deploy changes rapidly across distributed architectures. However, release risk assessment often relies on fragmented checklists, manual reviews, or intuition.

**Change Decision Intelligence (CDI)** provides:
- **Deterministic & Grounded Risk Assessment**: Evaluates changes objectively based on architecture criticality, blast radius, schema migrations, and similar historical evidence.
- **Explainable Policy Governance**: Enforces tenant-defined rules (e.g. mandatory review gates for high-criticality services) with full traceability.
- **Semantic Historical Evidence Retrieval**: Indexes past incidents, pull requests, and documentation using local on-device neural embeddings and vector similarity search.
- **Human-in-the-Loop Oversight**: Supports explicit human overrides with audit logging and mandatory rationale tracking.
- **Deployment & Outcome Lifecycle**: Tracks real-world release outcomes (`SUCCESS`, `FAILURE`, `INCIDENT`, `ROLLED_BACK`).
- **Observational Decision Attribution**: Evaluates the historical accuracy of risk predictions against production outcomes without retroactive data mutation.

---

## 2. System Architecture & Capabilities

CDI follows Hexagonal (Ports and Adapters) architecture with strict Domain-Driven Design (DDD) aggregate boundaries:

```
                  ┌─────────────────────────────────────────┐
                  │          REST Web Adapter / UI          │
                  └────────────────────┬────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Application & Domain Layer                            │
│                                                                                 │
│  [Change & Analysis] ──▶ [Risk Assessment] ──▶ [Policy Engine] ──▶ [Decision]   │
│          ▲                        ▲                                      │      │
│          │                        │ (Semantic Evidence)                  │      │
│          │              [Local ONNX Embedding Engine]                    ▼      │
│  [Deployment Tracking] ────────────────────────────────────▶ [Decision Attribution]
└──────────────────────────────────────┬──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │    PostgreSQL + pgvector Persistence    │
                  └─────────────────────────────────────────┘
```

### Core Capabilities:
1. **Change Ingestion & Snapshotting**: Lock evaluation to specific commit snapshots (`ChangeId`, `AnalysisRunId`).
2. **Deterministic Risk Engine**: Generates risk scores and factors (criticality, migration, dependency count, historical incident matches).
3. **Semantic Evidence Retrieval**: 384-dimensional dense neural embeddings (`sentence-transformers/all-MiniLM-L6-v2`) running completely locally via ONNX Runtime Java, stored in PostgreSQL via `pgvector` HNSW indexes.
4. **Policy & Decision Engine**: Evaluates tenant-scoped policy rules, generating explainable verdicts (`APPROVE`, `REVIEW_REQUIRED`, `BLOCK`).
5. **Human Override Governance**: Supports admin overrides while preserving original policy outputs for immutable auditing.
6. **Deployment & Outcome Tracking**: Records deployment lifecycles and operational outcomes with replay-safe idempotency.
7. **Decision Attribution**: Computes observational accuracy feedback (`ACCURATE_LOW_RISK`, `ACCURATE_HIGH_RISK`, `UNDERESTIMATED_RISK`, `OVERESTIMATED_RISK`, `UNATTRIBUTED`).

---

## 3. Technology Stack

- **Backend**: Java 21, Spring Boot 3.2.x, Spring Data JPA, Hibernate 6.4.x.
- **Database & Vectors**: PostgreSQL 16+, `pgvector` extension, Flyway schema migrations (V1–V14).
- **Machine Learning / Runtime**: Microsoft ONNX Runtime Java (`1.17.1`), Hugging Face `all-MiniLM-L6-v2` tokenization & embedding (no external API keys required).
- **Frontend**: React 18, TypeScript, Vite, Tailwind CSS, Lucide Icons.
- **Testing**: JUnit 5, MockMvc, Testcontainers (`pgvector/pgvector:pg16`), Vitest, React Testing Library.

---

## 4. Quickstart Guide

### Prerequisites
- Java 21+ JDK
- Node.js 18+ & npm
- Docker (for Testcontainers during integration tests)

### Starting the Backend
```bash
# Clone the repository
git clone <repository-url>
cd bits

# Run database migrations and start backend (defaults to port 8080)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Starting the Frontend
```bash
cd frontend
npm install
npm run dev
# The UI will be accessible at http://localhost:5173
```

### Running Tests
```bash
# Run all backend unit, integration, and Testcontainers tests (593 tests)
./mvnw clean test

# Run frontend tests (21 tests)
cd frontend
npm test

# Build frontend production bundle
npm run build
```

---

## 5. API Overview

All API endpoints are scoped to the authenticated tenant context:
- `POST /api/v1/changes/propose`: Propose a change for analysis.
- `POST /api/v1/changes/{changeId}/analyses`: Trigger analysis and risk assessment.
- `GET /api/v1/changes/{changeId}`: Retrieve change details, risk assessment, and decision verdicts.
- `POST /api/v1/evidence/search`: Perform semantic vector similarity search against past evidence.
- `POST /api/v1/decisions/{id}/override`: Execute human-in-the-loop override.
- `POST /api/v1/deployments`: Record a software release deployment.
- `POST /api/v1/deployments/{id}/outcomes`: Attach a verified production outcome (`SUCCESS`, `FAILURE`, `INCIDENT`, `ROLLED_BACK`).
- `GET /api/v1/attribution/{deploymentId}`: View observational attribution linking deployment outcome to historical risk.
- `GET /api/v1/attribution/summary`: View tenant and service decision accuracy statistics.

---

## 6. Project Status & Production Readiness

- **Functional Status**: **100% Feature-Complete** across all planned capabilities.
- **Test Baseline**: **614 automated tests passing** (593 backend + 21 frontend).
- **Production Note**: Current build uses development header-based security (`X-Tenant-Id`, `X-Actor-Role`). Before enterprise production rollout, refer to [`docs/production/production-readiness.md`](docs/production/production-readiness.md) for enterprise authentication, CORS hardening, secret management, and cluster deployment guidelines.