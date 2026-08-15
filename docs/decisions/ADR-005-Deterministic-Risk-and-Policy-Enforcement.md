# ADR 005: Deterministic Risk and Policy Enforcement

## Status
Draft

## Context
AI models can hallucinate or produce inconsistent results. Policy enforcement must be reliable.

## Decision
Risk and policy enforcement must be deterministic. AI will be used to propose, reason, and summarize, but deterministic rules will evaluate the final policy.

## Consequences
- Ensures safety and reliability.
- Requires building a deterministic policy engine alongside AI capabilities.
