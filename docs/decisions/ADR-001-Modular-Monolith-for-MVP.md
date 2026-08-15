# ADR 001: Modular Monolith for MVP

## Status
Draft

## Context
We are starting a new project. Microservices add operational overhead and complexity that is not yet justified.

## Decision
We will use a Spring Boot modular monolith architecture for the MVP. We will adhere to domain-driven boundaries to allow for easy extraction of services in the future if scalability requirements demand it.

## Consequences
- Lower operational complexity initially.
- Easier deployment and testing.
- Requires strict discipline to maintain module boundaries.
