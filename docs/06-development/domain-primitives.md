# Domain Primitives

This document defines the core domain primitive conventions used across all bounded contexts in the modular monolith.

## 1. Typed Identifiers
Instead of using raw `UUID` or `String` in method signatures (which leads to primitive obsession and accidental parameter swapping like `process(UUID repoId, UUID changeId)`), we use strongly typed identifiers implemented as Java `record`s.

Examples:
- `TenantId`
- `ChangeId`
- `AnalysisRunId`

**Immutability**: Java records guarantee structural immutability.
**Validation**: The compact constructor enforces invariants (e.g., value cannot be null).

*Note on UUIDv7*: While the persistent data model (PostgreSQL) mandates UUIDv7 for clustered indexing performance, the Domain layer remains agnostic to this and treats all IDs simply as standard `java.util.UUID`s. The infrastructure layer is responsible for generating UUIDv7 keys during persistence if native domain generation isn't supported out-of-the-box by standard Java libraries.

## 2. Time Strategy
The domain exclusively uses `java.time.Instant` for all timestamps.
- **Why**: It represents an exact point on the timeline in UTC. It avoids all timezone-related bugs common with `LocalDateTime` or `Date`. 

## 3. Domain Exceptions
We avoid polluting the domain layer with Spring's `ResponseStatusException` or JPA exceptions.
- **`DomainException`**: A minimal, generic `RuntimeException` base class located in the `common/domain` package. 
- Individual bounded contexts may subclass this (e.g., `PolicyViolationException`), but they must ultimately extend from `DomainException`. This ensures the application and adapter layers can catch and translate these into standard 4xx HTTP API responses.

## 4. Domain Events
A minimal `DomainEvent` interface provides the foundation for intra-module communication.
- **Contract**: Requires an `eventId()` and an `occurredOn()` timestamp.
- **Independence**: It does NOT extend `org.springframework.context.ApplicationEvent`. The infrastructure/application layer will wrap or route these pure domain events through Spring's event bus or a Transactional Outbox without forcing the domain entities to import Spring packages.
