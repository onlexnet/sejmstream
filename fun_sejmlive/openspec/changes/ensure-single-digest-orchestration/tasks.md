# Tasks

## 1. Architecture and lifecycle contracts

- [ ] 1.1 Align the ownership/recovery ADR and `../docs/port-contracts.md` with the confirmed fresh-state-only deployment scope, removing migration/adoption guidance while preserving the lifecycle, five-replacement budget, and ADR-009 identities; verify agreement with the revised spec. Documentation edit scope has been approved.
- [ ] 1.2 Keep versioned typed idle, owned, exhausted, and administrative-stop recovery-required state, including attempt ordinal and scheduled start; remove legacy decoding/gating and add JSON round-trip plus unsupported-payload rejection tests proving legacy `true` and `false` fail explicitly without becoming idle.
- [x] 1.3 Add the correlated runtime-observed dispatch subtype to `src/main/resources/openapi/collect-flow-schemas.yaml` and regenerate through the existing Maven generation phase; verify `mvn -DskipTests compile` and extend `DailyDigestIngestionCoordinatorDispatchCommandJsonRoundTripTest` for the new subtype while preserving existing discriminators.

## 2. Decisions, ownership, and retries

- [ ] 2.1 Retain correlated lifecycle decisions and remove legacy-specific transitions; verify request coalescing, pending ownership, hint-only notifications, stale observations, duplicate failure idempotency, and administrative-stop recovery in `DailyDigestIngestionCoordinatorDeciderTest`.
- [x] 2.2 Implement the 1/2/4/8/15-minute replacement policy and exhaustion in the decider, with no failure-message classification; verify all five delays, six-total-run cutoff, restart-preserved retry state, reset on success, and timer/ordinary-request rejection after exhaustion.
- [ ] 2.3 Retain returned-ID ownership and scheduled starts, remove actor legacy migration/gating branches, and reject unsupported persisted state; verify fresh absent-state initialization, one start, retained identity, delayed ownership, and rollback of state/actions on failed operations in actor tests.
- [x] 2.4 Preserve correlated orchestrator notifications and terminal return/throw semantics while treating notifications as reconciliation hints; run `SejmCollectOrchestratorFunctionTest` and add a focused regression if needed to prove blocking replay is not reported as failure.
- [x] 2.5 Update `../docs/sequences/seq-collect-daily-digest.puml` to include the actor, notification hint, terminal observation, and delayed replacement ownership; verify the sequence matches the implemented decision paths without changing sequential activity or downstream publishing behavior.

## 3. One-minute runtime observation

- [x] 3.1 Add a dedicated timer entrypoint and support service using the existing function scaffolding convention, with a shared function-name constant and `0 * * * * *` schedule; extend function-contract tests and Spring startup/wiring tests to verify discovery and the exact schedule.
- [ ] 3.2 Retain coordinator-state lookup, exact owned-instance metadata lookup, normalized observation signaling, and contextual error reporting; remove legacy-specific expectations and verify the observer never schedules loading orchestrations or fabricates terminal results for missing/invalid state, query errors, or absent metadata.
- [x] 3.3 Test actor/observer integration for missing completion/failure notifications, a hint delivered before terminal runtime state, overlapping observer ticks, and an observation delivered after ownership changes; verify pending/running/suspended/transitioning states retain ownership and terminated/canceled outcomes require explicit recovery.
- [ ] 3.4 Document the observation interval, non-SLA timing, retained ownership on uncertainty, metadata-retention dependency, and exhaustion diagnostics in `../docs/runbook.md`; verify its operational examples name the canonical actor and do not recommend force-starting or deleting its state.

## 4. Safe administrator recovery and fresh deployment

- [ ] 4.1 Route the existing `forceStartNext` command through safe recovery and update Telegram acknowledgement text; add recovery tests proving authorization is retained, owned/scheduled runs cannot be bypassed, ordinary fallback requests cannot clear exhaustion, and explicit recovery starts a fresh cycle after exhaustion.
- [ ] 4.2 Document fresh-deployment prerequisites in the runbook: absent coordinator state, no preexisting running/scheduled collection instances, old workers, or queued legacy commands; verify that no migration command, adoption path, or one-time migration setting is proposed, and warn against blind downgrade to boolean state.

## 5. Cross-cutting acceptance

- [ ] 5.1 Run the affected decider, actor, dispatch serialization, observer, orchestrator, timer/HTTP, Telegram recovery, function-contract, and Spring wiring suites together with the existing Maven runner; verify compilation, stable function/actor identities, and unchanged sequential collection and event publication.
- [ ] 5.2 Verify start/state coupling against an isolated Durable runtime, including entity-operation retry/failure and host restart; exercise lost notifications, delayed ownership under concurrent ingress, duplicate terminal observations, all five replacement failures, exhaustion, and explicit recovery, proving no overlapping coordinator-owned starts. Record actual results or a specific environment blocker without substituting mock results.
- [ ] 5.3 Run `mvn clean install` and `openspec validate ensure-single-digest-orchestration --strict`; verify all checks pass and include a final docs/code sync summary with any genuine remaining deployment limitations.
