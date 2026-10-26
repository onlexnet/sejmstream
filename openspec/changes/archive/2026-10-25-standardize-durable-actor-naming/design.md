# Design

## Context

See `proposal.md` for motivation and `specs/durable-actor-identity/spec.md` for the observable contract.

The current implementation has two business components implementing `EntityComponent`. `TaskEntityGateway` discovers those beans and routes by exact name. Both components currently lowercase mixed-case constants before registration; callers and trigger annotations use those constants directly.

| Surface | Current | Target |
|---|---|---|
| Coordinator implementation | `CollectCoordinatorEntity` | `CollectCoordinatorActor` |
| Snapshot implementation | `TermSnapshotReconcilerEntity` | `TermSnapshotReconcilerActor` |
| Coordinator entrypoint class | `SejmCollectCoordinatorEntityFunction` | `CollectCoordinatorEntityFunction` |
| Snapshot entrypoint class | `TermSnapshotReconcilerEntityFunction` | Unchanged |
| Declared coordinator type | `CollectCoordinator` | `collectcoordinator` |
| Canonical coordinator identity | `collectcoordinator` / `singleton` | Unchanged |
| Declared snapshot type | `SejmTermSnapshot` | `termsnapshotreconciler` |
| Canonical snapshot identity | `sejmtermsnapshot` / decimal term | `termsnapshotreconciler` / same decimal term |

`host.json` selects the Azure-managed Durable Task Scheduler provider and the configured task hub. There is no need to change either. Existing orchestration input includes the original coordinator entity ID and completion/failure signals return to that ID.

The snapshot actor compares an absent baseline against empty collections, classifies incoming items as new, and persists a merged baseline. Subsequent events use that actor instance's saved baseline.

## Goals / Non-Goals

**Goals:**

- Separate business actor terminology from Azure entity infrastructure.
- Make the same explicit canonical runtime names flow through annotations, callers, and registrations.
- Preserve normal snapshot baseline initialization and reuse.
- Preserve coordination continuity, business operation names, wire formats, and existing per-term isolation.

**Non-Goals:**

- State export/import, migration helpers, dual writes, legacy routing aliases, or forwarding.
- Resetting coordinator or snapshot state.
- Changing notification semantics, payload schemas, contract versions, Azure function-name strings, task hubs, or unrelated text-recognition/persistence names.
- Building deployment automation or a generic identity/key framework.

## Decisions

### Business components use Actor; Azure adapters retain Entity

Rename the two implementations and their directly corresponding tests and references. Keep `CollectCoordinatorContractV1`, `TermSnapshotReconcilerContractV1`, and existing contract operation-binding names. Azure entrypoints use `<Role>EntityFunction`; the SDK-facing `TaskEntityGateway`, `EntityComponent`, and durable infrastructure contracts retain entity terminology.

Keep package boundaries and behavior intact. This is not an instruction to replace every occurrence of `Entity` or to refactor lifecycle/state models.

Alternative: retaining `Entity` for business components would leave the ambiguity this change is intended to remove.

### Declare canonical identities in existing shared constants

Retain the stable `SejmCollectFunctions` class and constant identifiers. Set the coordinator and snapshot logical-name values to `collectcoordinator` and `termsnapshotreconciler`. Register those exact values without per-component lowercase conversion. All callers and trigger annotations continue to consume the constants.

Keep exact-match gateway routing, duplicate-registration failure, and component-provided operation validation. Do not derive names from class names, insert versions into runtime identities, or add aliases.

Alternative: deriving runtime names from implementation names would make a future refactor change persisted identity accidentally.

### Keys describe domain scope, not deployment or execution

Keep `singleton` for the coordinator and `String.valueOf(termNum)` for positive term numbers. Existing event boundary validation already rejects nonpositive terms. No run IDs, timestamps, environment prefixes, or versions are added; task-hub selection supplies the environment boundary.

Alternative: new prefixed keys would create additional fresh instances without adding useful scope information.

### Preserve snapshot baseline behavior

An actor instance without a baseline initializes normally. An existing instance continues from its own saved baseline rather than resetting it.

The first snapshot classifies incoming items as new and uses existing notification behavior. Subsequent identical snapshots produce no additional diff.

Alternative: resetting the baseline for every event would repeatedly classify known items as new.

## Risks / Trade-offs

- An absent baseline classifies incoming items as new -> cover initialization and repeated snapshots without changing reconciliation semantics.
- Mixed identity spelling silently targets the wrong instance -> verify trigger metadata, caller IDs, component registration, and SDK canonicalization together.

## Verification and Documentation Sync

- Extend focused contract/annotation and trigger tests to cover the exact new runtime IDs and unchanged Azure function names.
- Retain actor unit tests; add a focused absent-baseline then repeated-snapshot case if existing tests do not already cover it.
- Extend gateway `@AppTest` coverage only for missing exact-routing/duplicate-registration cases; retain positive bean discovery and broken-bean failure tests.
- Round-trip representative persisted state and existing contracts if actor renaming changes serialization references; no payload schema redesign is intended.
- Run targeted compile and affected suites before the repository-required final build.

**Docs used:** `../docs/port-contracts.md`, `../docs/runbook.md`, ADR-007's Durable Task Scheduler update, and current actor/gateway/trigger source and tests.

**Code planned:** the two actor implementations, coordinator entrypoint class, shared constants, directly affected references, and focused tests. No implementation code is changed by this proposal.

**Docs planned:** an ADR describing naming, canonical identities, and snapshot baseline behavior, plus applicable port-contract and architecture/contributor references. Documentation rooted outside this module must be updated from the repository root during implementation, respecting its applicable instructions.

**Temporary drift:** none; implementation and documentation describe the same naming, addressing, and baseline behavior.
