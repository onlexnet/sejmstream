# ADR-001: Hexagonal Architecture with Ports & Adapters

**Status:** Accepted  
**Date:** 2024-12-01

## Context

SejmStream integrates with multiple external systems (Sejm API, Facebook, Telegram, PostgreSQL, Azure Storage Queues) and is deployed on Azure Functions. The business logic needs to remain testable in isolation from infrastructure concerns, and adapters may change independently (e.g., switching from Facebook to another platform).

## Decision

Adopt Hexagonal Architecture (Ports & Adapters) with strict dependency rules:

- **Application Core** (`onlexnet.app`) contains use cases and port interfaces. It has zero dependencies on infrastructure frameworks.
- **Inbound Ports** are use case interfaces (`*UseCase`) accepting command objects and returning typed outcome sealed types.
- **Outbound Ports** are interfaces (`SejmApiClient`, `FacebookPublisher`, `TelegramNotifier`, `*Persistence`, `*QueuePort`) declared in the application layer.
- **Inbound Adapters** (`onlexnet.infra.adapters.in`) translate infrastructure triggers (Timer, HTTP, Queue) into use case invocations.
- **Outbound Adapters** (`onlexnet.infra.adapters.out`) implement outbound ports using concrete libraries (RestFB, JdbcTemplate, Azure SDK).
- **Dependency Direction**: Infrastructure → Application. Never the reverse.

Package structure enforced by ArchUnit test (`HexagonalBoundariesTest`).

## Consequences

**Positive:**
- Business logic is fully unit-testable without Azure Functions runtime or real databases
- Adapters are independently replaceable (e.g., swap Facebook for Mastodon)
- Clear separation of concerns improves maintainability
- Outcome sealed types make all possible results explicit and exhaustive

**Negative:**
- More interfaces and classes than a layered architecture
- Requires discipline to avoid shortcut dependencies
- New developers need to understand the pattern before contributing

**Risks:**
- Over-abstraction for simple flows (mitigated by keeping ports minimal)
