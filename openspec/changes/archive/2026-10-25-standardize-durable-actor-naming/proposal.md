# Proposal

## Why

Durable business components currently share the overloaded `Entity` terminology with Azure infrastructure and text recognition, while snapshot actor Java and runtime names describe different concepts. Establish a role-based actor convention with explicit, stable runtime identities.

## What Changes

- Rename business implementations to `CollectCoordinatorActor` and `TermSnapshotReconcilerActor`; retain `<Role>ContractV<n>` contracts and `Entity` terminology for Azure infrastructure.
- Standardize new durable type names as lowercase role names, explicitly declared in shared constants rather than derived from Java names.
- **BREAKING**: Change the snapshot durable type from canonical `sejmtermsnapshot` to `termsnapshotreconciler`.
- Declare the coordinator type explicitly as `collectcoordinator`, matching its existing canonical identity; preserve `singleton` and canonical decimal term-number keys.
- Preserve business operations, payloads, existing Azure function-name strings, and collection/reconciliation behavior.
- Document the convention, canonical runtime identities, and normal snapshot baseline behavior.

## Capabilities

### New Capabilities

- `durable-actor-identity`: Stable role-based actor addressing, exact runtime routing, and snapshot baseline initialization and reuse.

### Modified Capabilities

None. The project currently has no main capability specs.

## Impact

- Actor implementations, entrypoint class names, operation-binding references, shared identity constants, and their focused tests.
- Coordinator callers in HTTP/timer support, Telegram, and orchestration code; term event forwarding and reconciliation.
- Durable Task Scheduler actor identities in the existing task hub.
- Architecture/contributor documentation and port contracts under the repository documentation root; an ADR for the naming convention.
- No new business API, contract version, mandatory infrastructure dependency, or state-management tooling is intended.
