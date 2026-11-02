# Design

## Context

See `proposal.md` for motivation and the lifecycle delta spec for acceptance behavior.

Observed baseline before this change:

- `DailyDigestIngestionCoordinatorActor` starts `Fun_CollectOrchestrator` through `TaskEntityContext`, but discards the returned instance ID.
- Its persisted `Some` state and application decider carry only `running`. Incoming completion/failure IDs are not matched against state.
- The timeout branch schedules a one-hour delayed start while persisting idle; forced recovery starts regardless of ownership.
- `SejmCollectOrchestratorFunction` already sends correlated completion and failure commands. A signal is emitted before orchestrator return/throw and is not sufficient proof of terminal runtime status.
- Timer/HTTP support signals the coordinator; the existing collection timer runs hourly. Telegram recovery uses the existing `forceStartNext` command.
- `src/main/resources/openapi/collect-flow-schemas.yaml` owns the polymorphic dispatch contract. Existing tests cover dispatch JSON round-trips, coordinator decisions, actor behavior, function contracts, and Spring wiring.
- The installed Durable Task SDK exposes `TaskEntityContext.startNewOrchestration`, returning an instance ID, entity metadata retrieval, and `DurableTaskClient.getInstanceMetadata`. Runtime metadata queries belong in an Azure adapter, not the pure decider.
- ADR-009 fixes the actor address at `collectcoordinator@singleton`. ADR-002 and port contracts define sequential activities and their durable retry policy.

There are no existing main OpenSpec capabilities to modify. The separate naming change has no planning artifacts and is not part of this change.

## Goals / Non-Goals

**Goals:**

- Make ownership durable, correlated, and authoritative across starts, observations, and retries.
- Keep scheduling decisions in the actor and business transition rules in the pure application decider.
- Recover from lost notifications without introducing a second loading or monitoring orchestration.
- Make exhaustion and unsupported persisted state explicit rather than treating them as idle.

**Non-Goals:**

- Change collection activities, their retry policy, data fetching, downstream event publication, or digest publishing.
- Automatically terminate slow, suspended, or unqueryable orchestrations.
- Restart administratively terminated/canceled runs automatically. These are explicit operator-stop outcomes, not confirmed runtime failures.
- Guarantee cancellation of an already executing activity or exactly-once external writes.
- Introduce another scheduler service, database table, or dependency.
- Migrate legacy data, adopt preexisting collection instances, or add a migration command or deployment setting.

## Decisions

### 1. Versioned typed lifecycle instead of a boolean lock

Use an explicitly versioned persisted lifecycle with typed variants:

- `Idle`: no owned orchestration and a fresh retry budget.
- `Owned`: instance ID, attempt ordinal (0 for original, 1-5 for replacements), source, and scheduled start timestamp.
- `Exhausted`: failed instance identity and diagnostic context after attempt 5 fails.
- `RecoveryRequired`: a confirmed administrative terminal outcome, with the stopped instance identity.

Deployment targets fresh coordinator state. Absent state initializes idle; legacy boolean-only records,
unsupported versions, and invalid lifecycle payloads fail explicitly. They do not become idle or a migratable
business state. This fresh-state-only scope was explicitly confirmed during implementation on 2026-10-28.

Keep field lifecycle initialization separate from the persisted business lifecycle; neither uses nullable state/context fields. Persisted variants need Jackson discriminator metadata and round-trip tests. Represent optional diagnostic data explicitly rather than weakening state invariants.

Capture the actual ID returned by `TaskEntityContext.startNewOrchestration` and persist it as part of the same entity-operation result that schedules the orchestration. Schedule and ownership must be emitted together; do not make an out-of-band client start followed by a separate state write. Confirm this runtime coupling in an integration acceptance check.

Use a unique instance ID for every attempt, never reuse the previous ID, and retain the same canonical actor address. A scheduled future instance is already owned; no separate idle backoff window exists.

Alternative: retaining the boolean and inspecting global running instances cannot reliably correlate delayed messages or scheduled starts.

### 2. A dedicated one-minute status observer, with actor-only scheduling

Add one timer entrypoint and a support service, following existing trigger/support separation. Declare its stable function name in `SejmCollectFunctions` and use schedule `0 * * * * *`.

The observer:

1. Reads coordinator metadata and its typed persisted state.
2. If the state is `Owned`, queries metadata for that exact instance, without fetching orchestration inputs/outputs.
3. Signals the coordinator's existing dispatch operation with a typed runtime observation carrying the queried instance ID and normalized status.
4. Reports missing/invalid state, missing runtime metadata, and query/signal failures with instance context. Errors must not become fabricated terminal observations.

The observer never starts loading orchestrations. Reading state is advisory: another event can change ownership before its observation is delivered. The actor rechecks identity before applying it, making stale reads harmless. Multiple observer invocations can overlap without creating duplicate starts.

Timer execution is best effort, not a hard detection SLA; host outages and delivery latency extend recovery.

Alternative: a monitor orchestration adds another long-lived runtime lifecycle unnecessarily. Direct metadata RPCs inside actor decisions would mix infrastructure observations with serialized state transitions.

### 3. Signals request reconciliation, not immediate release

Preserve the completion/failure envelope and correlate its instance ID. A matching signal records a reconciliation hint for the owned instance; stale hints are ignored. It cannot change ownership, retry ordinal, or start a replacement by itself.

The next one-minute observer tick supplies authoritative runtime status, even if the notification was missing. A notification therefore does not require adding a second polling loop or direct client query inside the actor. A pending hint is cleared when ownership changes; it is diagnostic, not authorization.

Apply normalized runtime observations as follows:

| Runtime result | Actor behavior |
|---|---|
| Completed | Release matching owner, enter `Idle`, reset retries |
| Failed, attempt 0-4 | Schedule the next attempt with the corresponding backoff; own its new ID |
| Failed, attempt 5 | Enter `Exhausted`, report exhaustion, schedule nothing |
| Pending, Running, Suspended, ContinuedAsNew | Retain owner; no replacement |
| Terminated, Canceled | Release terminal owner into `RecoveryRequired`; require explicit recovery |
| Missing, unknown, query error | Retain owner; report uncertainty and continue observation |

No elapsed-time rule infers failure. Completion and failure hints that contradict runtime metadata cannot override it. After replacing A with B, every signal or observation for A is a no-op, so one failure cannot consume the budget twice.

Alternative: clearing the lock immediately on signal admits a race between notification delivery and runtime termination.

### 4. Durable bounded backoff, independent of failure text

Replacement ordinals 1-5 use delays of 1, 2, 4, 8, and 15 minutes. Measure each delay from the acceptance of the matching confirmed failure. Use the entity's orchestration start options for the future start and persist that exact schedule in `Owned`.

Persist the new ordinal only with successful scheduling in the entity-operation outcome. Do not increment it on hints, stale observations, metadata absence, or duplicate deliveries. On success, clear all retry history. Name and document the retry constants as the agreed recovery contract.

This replaces both timeout-string classification and the old one-hour retry. There are at most six runs in a cycle: the original and five replacements.

### 5. Explicit administrative recovery, without an ownership bypass

Keep `forceStartNext` as a compatibility envelope but route it to safe recovery:

- `Idle`: start one initial run.
- `Owned`: retain the owner and request reconciliation; never reset its budget or shorten its scheduled delay.
- `Exhausted`: reset the cycle and start one initial run.
- `RecoveryRequired`: a confirmed administrative stop permits an explicit recovery command to start a fresh cycle.

Ordinary timer/HTTP collection requests do not clear exhaustion or recovery-required state. Update Telegram acknowledgement text to describe a queued safe recovery request, not a guaranteed forced start. Its fallback ordinary request must not bypass these rules.

Existing command sources remain diagnostic, not authorization. Retain the current administrator access check at the Telegram boundary.

### 6. Evolve contract sources and preserve runtime invariants

Add a runtime-observed dispatch subtype in the source OpenAPI schema and regenerate DTOs through existing Maven generation. Keep current discriminators valid for already queued messages. Validate observations at the boundary, including nonempty instance identity and an explicit recognized status. Unknown runtime statuses must be logged and retained safely, never coerced to failure.

Add JSON round-trip tests for all newly persisted lifecycle variants and the new polymorphic command. Preserve existing orchestration completion/failure IDs and the guarantee that success returns and failure throws. Never swallow `OrchestratorBlockedException` while adding reconciliation behavior.

Alternative: sending untyped ad-hoc maps would bypass existing transport and serialization guardrails.

## Risks / Trade-offs

- Status checks and signal hints can race -> match the current unique ID on every transition; repeated terminal observations are idempotent.
- Host outages delay observation -> retain ownership durably and resume one-minute checks after recovery; do not promise a hard one-minute SLA.
- Missing/purged metadata prevents automatic recovery -> fail closed and require operator investigation; preserve metadata for owned runs.
- External management can resume or independently start orchestrations -> the single-owner guarantee applies to coordinator-mediated starts; prohibit out-of-band loading starts/resurrection operationally.
- Termination can leave an activity executing -> do not promise activity cancellation; operational recovery must consider in-flight side effects.
- Preexisting state or loading runs invalidate fresh deployment assumptions -> do not enable this version in a target containing them; data migration is outside this change.
- Runtime scheduling/state coupling is essential -> test actual entity-operation behavior before rollout; mocks alone do not prove this safety property.
- Repeated failures can stop hourly collection permanently -> expose exhausted state in logs and document the administrator recovery command.
- New persisted variants cannot be read safely by old code -> in-place rollback to the boolean-only actor is unsupported.

## Deployment Preconditions and Verification

1. Before implementation is finalized, document this architecture in a new ADR and update authoritative port contracts, collection sequence, and runbook. These live in `../docs/`; obtain the necessary edit-scope approval if the apply environment restricts changes to the module.
2. Verify that the target has no persisted collection-coordinator state, no running or future scheduled loading instances, no old workers, and no queued legacy collection commands. This is a deployment prerequisite, not a migration procedure; do not delete live state to make it appear satisfied.
3. Deploy the new actor, dispatch contract, observer, and recovery acknowledgements together. No migration flag, migration command, or adoption tooling is introduced.
4. Validate absent-state initialization, rejection of unsupported payloads, schema round-trips, coupled start/state behavior, and one-minute status observation before enabling collection ingress.
5. Smoke-test missing-notification recovery, delayed-retry ownership, stale messages, exhaustion, and explicit recovery in an isolated task hub. Do not inject failures into shared production collection.
6. Do not blindly downgrade persisted new-format state to the boolean-only actor. Any data conversion or rollback across formats is a separate operational change outside this scope.

## Documentation Sync

- Docs used: ADR-002, ADR-009, `../docs/port-contracts.md`, and `../docs/sequences/seq-collect-daily-digest.puml`.
- Code changes planned: decider, persisted actor lifecycle, observer timer/support, dispatch schema/DTOs, recovery presentation, and focused tests.
- Docs changes planned: new ownership/recovery ADR, port contracts, collection sequence, and operational deployment/recovery procedures.
- Current temporary drift: the partial implementation and ADR/port-contract edits still contain legacy decoding and gating from the original plan. Apply must remove that handling, reject unsupported payloads explicitly, update the affected tests/docs, and reverify reopened tasks before completion.
