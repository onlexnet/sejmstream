# ADR-009: Durable Actor Naming

**Status:** Accepted
**Date:** 2026-10-25

## Context

Business components and Azure runtime adapters have different responsibilities.
Sharing the overloaded `Entity` terminology with infrastructure and text recognition
obscures those roles. Actor addresses identify persisted state within the configured
Durable Task Scheduler task hub and must remain independent of Java implementation names.

## Decision

- Business components use `<Role>Actor`: `CollectCoordinatorActor` and
  `TermSnapshotReconcilerActor`. Contracts retain `<Role>ContractV<n>`.
- Azure adapters retain `<Role>EntityFunction`: `CollectCoordinatorEntityFunction`
  and `TermSnapshotReconcilerEntityFunction`. `TaskEntityGateway`, `EntityComponent`,
  Azure SDK types, text-recognition types, and persistence entities keep their names.
- Declare lowercase role names explicitly in `SejmCollectFunctions`; never derive
  persisted addresses from Java class names or contract versions. Use the same
  constants for annotations, callers, and exact component registration.
- Use exact-match gateway routing, explicit operation validation, and
  duplicate-registration startup failure.
- Keep actor identities and persisted payload formats independent of Java
  implementation names. Azure function names are separate infrastructure identifiers.

| Role | Canonical type | Scope key |
|------|----------------|-----------|
| Collection coordinator | `collectcoordinator` | `singleton` |
| Term snapshot reconciler | `termsnapshotreconciler` | Positive decimal term, e.g. `10` |

Term keys use `String.valueOf(termNum)` after positive-integer boundary validation.
The task hub, not an extra key prefix, provides the environment boundary.

## Consequences

Naming distinguishes business actors from Azure infrastructure without conflating
either with text-recognition or persistence entities. Explicit runtime names allow
Java refactoring without implicitly changing actor addresses.

A term actor without a saved baseline compares its first snapshot against empty
collections and classifies incoming items as new using normal notification behavior.
It persists a merged baseline for subsequent events; an identical repeated snapshot
produces no new diff.
