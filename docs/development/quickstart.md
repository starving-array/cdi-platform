# Developer Quickstart Guide

---

## 1. Prerequisites

- **Java**: OpenJDK 21 (LTS)
- **Node.js**: v18.0.0 or higher
- **npm**: v9.0.0 or higher
- **Docker**: Required for running PostgreSQL + pgvector Testcontainers during test execution

---

## 2. Backend Setup & Run

The backend is built with Spring Boot 3.2.x and Maven.

```bash
# Check Java version
java -version  # Should show Java 21

# Run the complete test suite (executes 593 tests against Docker Testcontainers)
./mvnw clean test

# Start the backend locally with the 'local' profile (starts on port 8080)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## 3. Frontend Setup & Run

The frontend is built with React, TypeScript, and Vite.

```bash
cd frontend

# Install dependencies
npm install

# Run frontend tests (21 tests)
npm test

# Build production bundle
npm run build

# Start development server (accessible at http://localhost:5173)
npm run dev
```

---

## 4. Useful API Test Examples

### Propose a Change (UC-01)
```bash
curl -X POST http://localhost:8080/api/v1/changes/propose \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: eng-1" \
  -H "X-Actor-Role: ENGINEER" \
  -d '{
    "repositoryId": "00000000-0000-0000-0000-000000000002",
    "externalChangeId": "PR-101",
    "title": "Migrate payment gateway to v2",
    "description": "Updates payment provider bindings",
    "author": "alice@example.com",
    "sourceBranch": "feature/pay-v2",
    "targetBranch": "main",
    "commitSha": "a1b2c3d4e5f67890123456789012345678901234"
  }'
```

### Search Semantic Evidence
```bash
curl -X POST http://localhost:8080/api/v1/evidence/search \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-Actor-Id: eng-1" \
  -H "X-Actor-Role: ENGINEER" \
  -d '{
    "query": "database connection pool exhaustion timeout",
    "limit": 5,
    "similarityThreshold": 0.65
  }'
```