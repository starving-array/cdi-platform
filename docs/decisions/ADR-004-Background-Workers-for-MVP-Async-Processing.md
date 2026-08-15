# ADR 004: Background Workers for MVP Async Processing

## Status
Draft

## Context
We have tasks like repository ingestion and AI analysis that need to run asynchronously.

## Decision
We will use background workers (e.g., within the Spring Boot monolith) combined with PostgreSQL or Redis for queueing, avoiding heavy message brokers like Kafka for the MVP.

## Consequences
- Simpler infrastructure.
