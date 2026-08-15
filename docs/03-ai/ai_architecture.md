# AI Architecture

## Primary Agent: Risk Investigation Agent
**Responsibilities:**
- inspect the proposed change
- identify uncertainty
- retrieve relevant evidence
- investigate dependencies
- investigate historical incidents
- compare similar changes
- synthesize evidence
- produce structured findings
- recommend actions

**Constraints:**
The agent must NOT:
- merge PRs
- deploy
- modify production
- change policies
- override deterministic safety rules

All agent outputs must be structured and schema validated.

## Risk VS Decision
- **Risk engine**: Answers "How risky is this change?"
- **Decision engine**: Answers "Given risk + evidence + criticality + organizational policy, what should happen?"
