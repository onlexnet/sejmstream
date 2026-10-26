# durable-actor-identity Specification

## Purpose

Provide stable, scope-based addressing for durable actors and predictable initialization and reuse of per-instance snapshot baselines.

## Requirements

### Requirement: Canonical actor addressing

The system SHALL address actors by an explicitly declared lowercase role name and a stable scope key. Collection coordination SHALL use type `collectcoordinator` and key `singleton`. Term reconciliation SHALL use type `termsnapshotreconciler` and a positive term number formatted as canonical decimal text. Implementation renames and contract versions SHALL NOT implicitly change an actor identity.

#### Scenario: Multiple collection entrypoints share coordination
- **WHEN** timer, HTTP, or Telegram requests collection in the same task hub
- **THEN** each request targets `collectcoordinator` with key `singleton`

#### Scenario: Term events select the correct baseline
- **WHEN** a collection event for term 10 is accepted
- **THEN** reconciliation targets `termsnapshotreconciler` with key `10`
- **AND** an event for another term targets a distinct actor instance

### Requirement: Exact actor routing

The system SHALL resolve actor types by exact canonical name, reject unknown types or unsupported operations explicitly, and fail startup for duplicate type registrations. It SHALL NOT use case-insensitive fallback or silently initialize an unrelated actor when routing fails.

#### Scenario: Unknown actor type
- **WHEN** an invocation names an unregistered actor type
- **THEN** processing fails explicitly without running another actor

#### Scenario: Duplicate registrations
- **WHEN** two actor components register the same canonical type
- **THEN** application startup fails with the conflicting type identified

### Requirement: Snapshot baseline lifecycle

The system SHALL compare a snapshot against an empty baseline when the term actor has no saved baseline. It SHALL persist the resulting baseline and reuse it for subsequent events without resetting it.

#### Scenario: No saved baseline exists
- **WHEN** the first event arrives for term 10 and that term actor has no saved baseline
- **THEN** reconciliation compares the incoming snapshot against an empty baseline
- **AND** incoming items are classified as new using existing notification behavior

#### Scenario: Target baseline already exists
- **WHEN** another event arrives for a target that has processed a snapshot
- **THEN** reconciliation uses that target's saved baseline rather than resetting it

#### Scenario: Identical snapshot is repeated
- **WHEN** a term actor receives a snapshot identical to its saved baseline
- **THEN** reconciliation produces no new diff

### Requirement: Coordinator identity stability

The system SHALL keep collection coordination state scoped to `collectcoordinator` and `singleton` within a task hub. Implementation names and contract versions SHALL NOT select a different coordinator or reset its persisted state.

#### Scenario: Implementation name or contract version changes
- **WHEN** collection is requested after an implementation rename or contract version change in the same task hub
- **THEN** the request addresses the same coordinator singleton
- **AND** existing coordination state remains available
