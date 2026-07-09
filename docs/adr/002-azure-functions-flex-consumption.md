# ADR-002: Azure Functions Flex Consumption with Durable Functions

**Status:** Accepted  
**Date:** 2024-12-01

## Context

SejmStream needs to:
1. Run scheduled tasks (hourly collection, daily publishing)
2. Process queue messages reliably
3. Handle HTTP webhooks (Telegram)
4. Execute 6 independent API calls in parallel during collection
5. Scale to zero when idle (cost optimization for a side project)

## Decision

Deploy on **Azure Functions Flex Consumption Plan** (Java 21) with **Durable Functions** for orchestration:

- **Flex Consumption** provides scale-to-zero, per-execution billing, and up to 100 max instances.
- **Durable Functions Orchestrator** (`Fun_CollectOrchestrator`) executes 6 activities **sequentially** with retry policy (maxRetries=3, delay=10s, backoffCoefficient=2.0, maxInterval=2min, timeout=10min). Sequential execution simplifies error handling and avoids API rate limiting.
- **Timer Triggers** for scheduling: hourly collection (`0 0 * * * *`), daily publishing (`0 30 23 * * *`).
- **Queue Trigger** for interpellation processing with built-in poison message handling.
- **HTTP Triggers** for Telegram webhook and manual admin operations.
- **System-Assigned Managed Identity (MSI)** for Key Vault access and storage authentication.

## Consequences

**Positive:**
- Zero cost when idle (no always-on compute)
- Parallel collection completes faster than sequential
- Built-in retry and poison queue handling for queue triggers
- Durable Functions maintain orchestration state across restarts
- MSI eliminates credential management for Azure resources

**Negative:**
- Cold start latency (~2-5s for Java) on first invocation
- Durable Functions add complexity (state serialization, replay semantics)
- Known Kudu MSI token bug requires `StorageAccountConnectionString` workaround
- Flex Consumption is relatively new; fewer community resources

**Risks:**
- Durable Functions state corruption (mitigated by idempotent activities)
- Timer drift if function app is under heavy load (acceptable for this use case)
