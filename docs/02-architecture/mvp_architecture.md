# MVP Architecture

## Overview
- Frontend
- Spring Boot modular monolith
- Domain modules
- PostgreSQL
- pgvector where semantic retrieval is required
- Redis where caching/state is justified
- background workers for asynchronous processing
- LLM/AI services

GitHub should be the first source-control integration. Use ports/adapters to allow future integrations without coupling domain logic to GitHub APIs.

## Domain Boundaries
**Initial:**
- organization
- change
- context
- evidence
- risk
- decision
- policy
- agent

**Future:**
- deployment, outcome, attribution, learning, model management.
