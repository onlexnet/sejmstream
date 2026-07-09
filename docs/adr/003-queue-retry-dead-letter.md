# ADR-003: Queue-Based Retry with Dead-Letter for Interpellation Publishing

**Status:** Accepted  
**Date:** 2025-01-15

## Context

Interpellations are collected hourly and need to be published to Facebook individually. Facebook API has rate limits and transient failures. We need a reliability pattern that:
- Retries failed publishes with increasing delays
- Prevents duplicate publications
- Eventually gives up on permanently failing messages
- Provides visibility into failed messages for manual intervention

## Decision

Implement a **queue-based retry pattern** with explicit state tracking:

### Queues
- **Main queue**: `sejm-interpellations-publish` — messages for publishing
- **Dead-letter queue**: `sejm-interpellations-publish-deadletter` — permanently failed messages

### State Machine
Each interpellation publish goes through states tracked in `sejm_interpellation_publish_state`:
```
QUEUED → PROCESSING → PUBLISHED
                    → PUBLISH_CONFIRMATION_PENDING (ambiguous failure)
                    → RETRY_SCHEDULED → (back to PROCESSING on next attempt)
                    → DEAD_LETTER (after max attempts)
                    → QUEUE_ENQUEUE_FAILED (enqueue error)
```

### Retry Policy (`InterpellationPublishRetryPolicy`)
- Max attempts: 5 (configurable via `interpellation.publish.queue.max-attempts`)
- Base delay: 60 seconds (configurable via `interpellation.publish.queue.retry-delay-seconds`)
- Backoff multiplier: 2.0 (configurable via `interpellation.publish.queue.backoff-multiplier`)
- Max retry delay: 900 seconds (configurable via `interpellation.publish.queue.max-retry-delay-seconds`)
- Formula: `min(maxDelay, baseDelay × multiplier^(attempt-1))`
- Example delays: 60s → 120s → 240s → 480s → 900s (capped)
- Mechanism: Re-enqueue to same queue with `VisibilityTimeout` as `Duration` (delayed delivery)

### Claim-based Deduplication
- `isPublished()` checks if already published before processing
- `tryClaimForPublish()` uses database state transition (`status → PROCESSING`) with atomic update to prevent concurrent processing of the same interpellation

## Consequences

**Positive:**
- Transient failures are retried automatically
- Permanent failures are isolated in dead-letter queue for manual review
- Database state provides full audit trail of attempts
- Claim mechanism prevents duplicate Facebook posts
- Configurable retry parameters without code changes

**Negative:**
- More complex than simple in-process retry (requires queue + DB state)
- Visibility timeout-based delay is approximate (not exact scheduling)
- Dead-letter queue requires manual monitoring/intervention

**Risks:**
- Queue message format changes require backward compatibility handling
- Database state and queue state can become inconsistent (mitigated by claim-first pattern)
