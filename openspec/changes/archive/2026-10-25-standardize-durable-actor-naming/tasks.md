# Tasks

## 1. Business actor terminology

- [x] 1.1 Rename the business implementations to `CollectCoordinatorActor` and `TermSnapshotReconcilerActor`, and the coordinator entrypoint class to `CollectCoordinatorEntityFunction`; update direct production/test references without changing packages, operations, contracts, or Azure function-name strings. Verify targeted test compilation and the renamed actor behavior suites.
- [x] 1.2 Update affected annotation, wiring, and operation-binding tests alongside the renames; verify Spring discovers the renamed components, their existing contracts still dispatch, and entrypoint annotations preserve deployed function-name strings.
- [x] 1.3 Record the naming convention in a repository ADR and update applicable contributor/architecture references, including the snapshot actor references in `../docs/port-contracts.md`. Verify documentation consistently distinguishes business `Actor` components from Azure `Entity` infrastructure.

## 2. Canonical runtime identity and snapshot baselines

- [x] 2.1 Set the existing shared runtime-name constants to `collectcoordinator` and `termsnapshotreconciler`, remove redundant per-component lowercase conversion, and retain `singleton` and canonical positive term-number keys. Verify actual SDK entity IDs, trigger metadata, and registration values agree; confirm the coordinator's canonical ID is unchanged.
- [x] 2.2 Update HTTP/timer support, Telegram, orchestrator, and collect-event trigger tests to assert their exact target identities and existing operation names. Verify collection requests share one singleton, term events remain isolated by key, and no normal caller targets the legacy snapshot type.
- [x] 2.3 Preserve exact-match gateway routing and explicit operation validation; add only missing unknown-type, unsupported-operation, or duplicate-registration tests in the existing gateway coverage. Verify positive bean discovery/routing and the negative broken-bean scenario continue to pass under `@AppTest`.
- [x] 2.4 Preserve existing snapshot initialization and reconciliation behavior; cover an absent target receiving a snapshot followed by the same snapshot again in the focused actor suite. Verify the first event classifies incoming items as new, the repeated event produces no new diff, and the implementation contains no legacy-state lookup, import, automatic reset, or deletion path.
- [x] 2.5 Keep persisted-state and message JSON contracts compatible with the Java renames; retain existing round-trip coverage and add a representative case only where serialization references are affected. Verify deserialization of representative existing coordinator state and operation payloads without requiring a new contract version.
- [x] 2.6 Align the ADR, port contracts, and architecture references with canonical actor identities and normal snapshot baseline initialization and reuse. Keep one-time rollout decisions out of the long-lived specification and documentation.

## 3. Cross-surface integration verification

- [x] 3.1 Run targeted compilation and the affected actor, gateway, function-contract, trigger, orchestration, and Spring-wiring suites together; verify generated Azure binding metadata uses the canonical actor types while retaining deployed function-name strings.
- [x] 3.2 Run the repository-required `mvn clean install`, inspect the final diff for unintended payload/configuration changes or migration code, and include the documentation sync summary. Verify the build succeeds and only the agreed actor naming, addressing, tests, and documentation surfaces changed.

## Documentation sync

- Docs used: repository domain model, port contracts, runbook, ADR-007's Durable Task Scheduler update, and module architecture/contributor instructions.
- Code changed: business actor and coordinator entrypoint names, explicit canonical runtime constants and registrations, and focused addressing/routing/reconciliation tests. Packages, operations, state/message JSON, host configuration, and deployed function names are unchanged.
- Docs changed: ADR-009, current snapshot actor references in ADR-008 and port contracts, module architecture/README, contributor naming guidance, and scheduler-backend troubleshooting in the runbook.
- Temporary drift: none in code/documentation.

The change was moved unchanged to the existing repository-level OpenSpec root with user approval so both module and repository documentation edits are in scope. Paths such as `../docs/` above are relative to `fun_sejmlive/`; run OpenSpec follow-ups from `/sejmstream`.
