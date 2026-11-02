# Proposal

## Why

The daily-digest coordinator stores only a running flag, so missing failure signals can block collection indefinitely, while delayed timeout retries and forced recovery can create overlapping runs. Explicit ownership and bounded recovery are needed to keep collection live without sacrificing single-run execution.

## What Changes

- Persist the identity of the one owned collection orchestration, including scheduled starts, rather than only a running flag.
- Correlate completion signals, failure signals, and runtime observations with that identity; ignore stale or duplicate messages.
- Add a one-minute runtime status check, with scheduling decisions remaining exclusively in the actor.
- Require terminal runtime confirmation before releasing ownership; missing metadata and query errors never authorize another start.
- Replace timeout-message matching and the one-hour retry with five automatic replacements delayed by 1, 2, 4, 8, and 15 minutes. Success resets recovery history.
- Stop automatic recovery after the fifth replacement fails. Timer and ordinary collection requests cannot reset exhaustion; an administrator must explicitly recover.
- **BREAKING**: retain the existing recovery command envelope but remove its ability to force an overlapping start.
- Target fresh coordinator state only. Legacy boolean-only state is unsupported and must be rejected explicitly;
  no migration command, adoption path, or one-time migration setting is included.

## Capabilities

### New Capabilities

- `daily-digest-orchestration-lifecycle`: Single-instance ownership, correlated terminal-state reconciliation, and bounded failure recovery for daily-digest collection.

### Modified Capabilities

None. The project currently has no durable OpenSpec capability definitions.

## Impact

- Application core: `DailyDigestIngestionCoordinatorDecider` state, commands, decisions, and focused tests.
- Azure adapters: coordinator actor and state, collection orchestrator notifications, a dedicated monitoring timer/support service, shared function constants, and Telegram recovery behavior.
- Contracts: source OpenAPI collect-flow definitions, generated command DTOs, persisted lifecycle serialization, and JSON round-trip tests.
- Existing Durable Task SDK metadata and entity APIs suffice; no new external service or dependency is proposed.
- Documentation: collection sequence, port contracts, runbook, and an ADR describing ownership and recovery. Their authoritative files currently live in `../docs/`.
- Existing actor address `collectcoordinator@singleton`, collection function names, sequential activities, activity retry policy, and downstream publishing behavior remain unchanged.
- Deployment requires absent coordinator state and no preexisting collection runs or queued legacy commands in the target environment.
