# Project Charter

## Product Vision
An evidence-grounded pre-merge engineering change decision system.

## Problem Statement
Current adjacent products include release-readiness and release-decision platforms. However, they lack evidence-grounded intelligence that combines deterministic analysis with context, historical evidence, criticality, and AI reasoning. We need a system that can explain WHY a change is risky, WHAT evidence supports it, and WHAT action reduces risk.

## Objectives
- Build an evidence-grounded pre-merge engineering change decision system.
- Combine deterministic software-change analysis, system context, historical engineering evidence, business criticality, and agentic investigation.
- Produce explainable decision outputs based on policy evaluation.

## V1 Scope (P0 & P1)
**P0:**
- GitHub PR ingestion
- change metadata
- diff analysis
- deterministic risk signals
- service/dependency context
- historical evidence
- semantic evidence retrieval
- risk assessment
- investigation agent
- policy engine
- decision recommendation
- evidence-backed explanation
- dashboard
- evaluation benchmark

**P1:**
- lightweight ML risk model
- richer dependency analysis
- GitHub checks
- configurable policies
- simulated deployment
- basic outcome recording

## Non-Goals (Current Phase)
- Production deployment integrations
- Automated incident attribution
- Continuous model retraining
- Organization-specific calibration
- Multi-provider integrations
- Autonomous remediation / automated rollback / autonomous production deployment
