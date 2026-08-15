# ADR 007: Evidence-Grounded AI Decisions

## Status
Draft

## Context
We need to provide explainable decision outputs.

## Decision
All AI outputs and decisions must be grounded in evidence. The system must explain WHY a change is risky, WHAT evidence supports it, and WHAT uncertainty remains.

## Consequences
- Requires building robust tracing from decision back to source evidence.
