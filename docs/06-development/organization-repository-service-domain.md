# Organization, Repository, and Service Domain

This document summarizes the design of the first business-domain slice establishing the enterprise context.

## Aggregate Boundaries
- **Organization**: Independent Aggregate Root (`com.cdi.organization.domain.Organization`). Represents the enterprise tenant.
- **Repository**: Independent Aggregate Root (`com.cdi.change.domain.Repository`). A source-control concept representing where code lives. Placed in the `Change` context conceptually as per bounded-context definition.
- **Service**: Independent Aggregate Root (`com.cdi.systemcontext.domain.Service`). A software-system concept representing what the code actually runs as, along with business ownership and criticality. 

*Rationale for separation*: A monolithic `Organization` aggregate holding all repositories and services would create massive concurrency contention and violate aggregate design principles. They are kept as independent aggregates referencing each other via typed IDs.

## Important Invariants
- **Service Dependencies**: 
  - A Service cannot depend on itself.
  - A Service can only depend on another Service belonging to the *same* `TenantId`. This guarantees strict multi-tenant isolation at the domain level.
- **Provider Neutrality**: The `Repository` domain model does not depend on GitHub SDKs. It natively supports Git platforms via the `ProviderType` enum and `externalId`.

## State Transitions
State transitions are executed through explicit domain methods, preventing arbitrary external mutation via setters:
- `Organization.suspend()` / `reactivate()`
- `Repository.archive()`
- `Service.deprecate()`

All state methods throw `DomainException` if an invalid transition is attempted (e.g., deprecating an already deprecated service).

## Identifiers
- Uses the strongly-typed `TenantId`, `RepositoryId`, and `ServiceId` records to eliminate primitive obsession.
