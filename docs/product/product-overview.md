# Change Decision Intelligence (CDI) — Product Overview

---

## 1. The Core Problem

High-velocity engineering teams face an ongoing tradeoff between deployment speed and operational stability:
- Minor changes can trigger major outages when touching critical shared dependencies or unverified database schemas.
- Manual peer reviews are subjective, inconsistent, and often miss historical context from past incidents.
- Post-incident reviews rarely translate into actionable pre-merge governance.

---

## 2. The CDI Solution

CDI acts as an intelligent decision intelligence layer embedded directly into the engineering workflow:

1. **Continuous Risk Evaluation**: When a pull request is created or updated, CDI analyzes the code snapshot, system criticality tier, downstream dependencies, and database migrations.
2. **Organizational Memory**: CDI retrieves past incidents, post-mortems, and relevant architectural decisions using semantic vector search.
3. **Deterministic Policy Enforcement**: Tenant-defined rules evaluate objective risk factors to provide clear, actionable verdicts (`APPROVE`, `REVIEW_REQUIRED`, `BLOCK`).
4. **Transparent Oversight**: Engineers can review decision rationales, inspect supporting evidence citations, or execute authorized overrides with audit logging.
5. **Closed-Loop Attribution**: CDI correlates real-world deployment outcomes against pre-merge predictions, measuring risk accuracy across services and teams.

---

## 3. Key Business Values

- **Reduced Mean Time to Detect (MTTD) Risk**: Catches risky architectural patterns before code merges to production.
- **Audit-Ready Governance**: Every decision is permanently traceable to the exact commit snapshot, policy version, and risk calculation.
- **Zero External SaaS Lock-In**: Runs entirely within enterprise infrastructure with embedded vector intelligence.