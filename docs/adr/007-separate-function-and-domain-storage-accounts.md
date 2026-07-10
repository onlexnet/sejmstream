# ADR-007: Separate Storage Accounts for Function Runtime and Domain Logic

**Status:** Accepted
**Date:** 2026-07-10

## Context

The Azure Function App previously used a single Azure Storage Account
(`azurerm_storage_account.function_app`) for two unrelated concerns:

1. **Function runtime/host requirements** — required by Azure Functions Flex Consumption
   itself: `AzureWebJobsStorage`, the deployment package container, and Durable Functions
   task hub state (control queues, history tables).
2. **Domain logic** — the `sejm-interpellations-publish` and
   `sejm-interpellations-publish-deadletter` queues used by
   `AzureStorageInterpellationPublishQueue` and the `Fun_InterpellationPublishFromQueue`
   queue trigger to implement the retry/dead-letter pattern from ADR-003.

Mixing these on one storage account coupled the lifecycle, throughput, and access-control
boundaries of domain data with infrastructure required purely to run the Function App.
`local.settings.json` already anticipated the split by declaring both `AzureWebJobsStorage`
and a separate `Storage` setting, but the domain adapter and queue trigger still read
`AzureWebJobsStorage`.

## Decision

Provision two Azure Storage Accounts:

- **Function storage account** (`azurerm_storage_account.function_app`, name pattern
  `sejmstr{env}fn*`) — function-related only. Backs `AzureWebJobsStorage`, the deployment
  container, and Durable Functions task hub state. Nothing domain-specific is stored here.
- **Domain storage account** (`azurerm_storage_account.domain`, name pattern
  `sejmstr{env}dom*`) — owns the interpellation publish queue and its dead-letter queue.
  Exposed to the app via the `Storage` app setting.

Code changes:
- `AzureStorageInterpellationPublishQueue` reads its connection string from `Storage`
  instead of `AzureWebJobsStorage`.
- `InterpellationPublishQueueFunctions`'s `@QueueTrigger` binds with
  `connection = "Storage"` instead of `"AzureWebJobsStorage"`.

Terraform changes:
- New `azurerm_storage_account.domain` resource; queues moved onto it.
- Function App managed identity and the Terraform deployer get `Storage Queue Data
  Contributor` on the domain storage account (in addition to the existing role
  assignments on the function storage account needed for Durable Functions control
  queues/tables).
- New outputs: `domain_storage_account_id`, `domain_storage_account_name`,
  `domain_storage_queue_service_endpoint`; queue URL outputs now resolve against the
  domain storage account.

## Consequences

**Positive:**
- Function-related infrastructure (required simply to create/run the Azure Function) is
  isolated from domain data, matching hexagonal architecture boundaries (ADR-001).
- Domain storage can be scaled, monitored, and access-controlled independently of the
  Functions host.
- Clearer least-privilege role assignments: the domain storage account only grants queue
  access, not blob/table access needed by the Functions host.

**Negative:**
- One additional Azure resource (storage account) to provision and pay for.
- Local development must keep `AzureWebJobsStorage` and `Storage` in sync when pointed at
  different emulators/accounts (Azurite covers both by default).

**Risks:**
- Existing deployments must be migrated: queues are re-created on the new storage
  account, so in-flight messages on the old account should be drained before cutover.
