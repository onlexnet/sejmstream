# ADR-010: Daily Digest Orchestration Ownership and Recovery

**Status:** Accepted
**Date:** 2026-10-27

## Context

A boolean collection lock cannot identify a failed run or distinguish a scheduled
retry from idle. Notifications may arrive before runtime termination or never arrive.
Unconditional manual recovery can overlap collection runs.

## Decision

- Keep the ADR-009 address `collectcoordinator@singleton`.
- Persist a versioned typed lifecycle: idle, owned, exhausted, or recovery-required.
  An owned instance includes its ID, attempt ordinal, source, and scheduled start.
- Couple orchestration scheduling and ownership persistence in one durable entity
  operation. Future scheduled starts already count as owned.
- Treat completion/failure signals as correlated reconciliation hints, not terminal
  runtime confirmation. Ignore stale IDs and apply each terminal outcome once.
- A separate timer checks the owned instance every minute and signals observations.
  Only the actor schedules loading orchestrations. No monitor orchestration is added.
- Confirmed completion resets recovery. Confirmed failure schedules replacements
  after 1, 2, 4, 8, and 15 minutes. After the fifth replacement fails, stop until an
  administrator explicitly recovers. Ordinary timer/HTTP requests cannot reset this.
- Unknown, missing, suspended, pending, running, or unqueryable status never
  authorizes another run. Administrative termination/cancellation requires recovery.
- Retain the `forceStartNext` command envelope but remove its ownership bypass.
- Target fresh coordinator state with no preexisting collection runs or queued
  legacy commands. Legacy boolean-only state is unsupported and fails explicitly;
  no migration or adoption mechanism is provided.

## Consequences

Collection remains serialized even during delayed retries and lost notifications.
Checks are best effort: host outages extend recovery beyond the one-minute interval.
Do not purge owned-instance metadata or start/resurrect loading instances outside
the coordinator. Runtime termination does not guarantee cancellation of activities.
Do not delete live state to satisfy fresh-deployment prerequisites or blindly
downgrade new-format state to the boolean-only actor.
Sequential activity retries and downstream publishing are unchanged.
