# ADR 003: PostgreSQL pgvector for Initial Semantic Retrieval

## Status
Draft

## Context
We need semantic retrieval capabilities (RAG) but want to avoid the overhead of a dedicated vector database for the MVP.

## Decision
We will use PostgreSQL with the `pgvector` extension for semantic retrieval.

## Consequences
- Simplifies infrastructure by keeping vector data in the primary database.
- Sufficient for MVP scale.
