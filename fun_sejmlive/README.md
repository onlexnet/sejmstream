# fun_sejmlive

Azure Functions (Java + Spring Boot) application that collects daily Sejm activity, stores digest data, and publishes updates to Facebook and Telegram-administered channels.

## What this module does

- Collects Sejm data on a schedule via Durable Functions orchestration.
- Stores digest items and publish state in PostgreSQL (Liquibase-managed schema).
- Publishes daily digest to Facebook (timer + manual HTTP trigger).
- Processes INTERPELLATION publish jobs from Azure Storage Queue with retry and dead-letter handling.
- Maintains a dedicated durable term snapshot entity that compares current and previous term state and raises recognized change events.
- Exposes Telegram webhook for admin commands (`/help`, `/data`, `/collect`, `/collect_recover`, `/publish`, `/version`).

## Architecture

This project follows Hexagonal Architecture (Ports and Adapters). See [APP_ARCH.md](APP_ARCH.md) for details.

Main boundaries:

- `onlexnet.app` - use cases and ports.
- `onlexnet.infra.adapters.in` - Azure Function input adapters.
- `onlexnet.infra.adapters.out` - Sejm API, storage queue, DB, Telegram/Facebook output adapters.
- `onlexnet.infra.starters` - Spring Boot startup and Azure Functions DI integration.

## Function entrypoints

### Collect pipeline (Durable Functions)

- `Fun_SejmCollectTimer` - `TimerTrigger`, cron `0 0 * * * *` (hourly).
- `Fun_CollectStart` - `HttpTrigger` `POST`, auth level `FUNCTION`.
- `Fun_CollectOrchestrator` - durable orchestrator.
- `Fun_CollectCoordinatorEntity` - durable entity to serialize collect requests.
- `Fun_SejmTermSnapshotEntity` - durable entity to keep latest term snapshot and dispatch per-event handlers after diffing.
- Orchestrator sends currently collected snapshot state (from activity outputs) directly to the term snapshot entity for diffing.
- Coordinator entity exposes admin recovery operation `forceStartNext` (durable entity op) to resume queue processing when state is stuck and no orchestrator is running.
- Activities:
   - `Intern_CollectVotings`
   - `Intern_CollectCommittees`
   - `Intern_CollectPrints`
   - `Intern_CollectInterpellations`
   - `Intern_CollectQuestions`
   - `Intern_CollectBills`

### Facebook digest publish

- `Fun_FacebookPublish` - `TimerTrigger`, cron `0 30 23 * * *`.
- `Fun_FacebookPublishStart` - `HttpTrigger` `POST`, auth level `FUNCTION`, route `/api/Fun_FacebookPublishStart`.

### Interpellation queue publish

- `Fun_InterpellationPublishFromQueue` - `QueueTrigger` on `%INTERPELLATION_PUBLISH_QUEUE_NAME%`, connection `DomainStorage`.

### Telegram webhook

- `Fun_TelegramWebhook` - `HttpTrigger` `POST`, auth level `ANONYMOUS`, route `/api/telegram/webhook`.

## Configuration

Start from [local.settings.json.example](local.settings.json.example).

Required at runtime:

- `FUNCTIONS_WORKER_RUNTIME=java`
- `AzureWebJobsStorage` (Functions host storage; for local use `UseDevelopmentStorage=true`)
- `DomainStorage` (domain queue storage; can also be `UseDevelopmentStorage=true` locally)
- `DB_URL` (PostgreSQL JDBC URL)
- `DB_USERNAME`
- `DB_PASSWORD`
- `FB_TOKEN`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_ALLOWED_CHAT_ID`

Common optional settings:

- `APPLICATIONINSIGHTS_CONNECTION_STRING`
- `APPINSIGHTS_INSTRUMENTATIONKEY` (legacy)
- `LIQUIBASE_ENABLED` (defaults to `false` via `application.properties`)
- `sejm.api.base-path` (defaults to `https://api.sejm.gov.pl`)
- `INTERPELLATION_PUBLISH_QUEUE_NAME` (default `sejm-interpellations-publish`)
- `INTERPELLATION_PUBLISH_DEAD_LETTER_QUEUE_NAME` (default `sejm-interpellations-publish-deadletter`)
- `INTERPELLATION_PUBLISH_MAX_ATTEMPTS` (default `5`)
- `INTERPELLATION_PUBLISH_RETRY_DELAY_SECONDS` (default `60`)
- `INTERPELLATION_PUBLISH_BACKOFF_MULTIPLIER` (default `2.0`)
- `INTERPELLATION_PUBLISH_MAX_RETRY_DELAY_SECONDS` (default `900`)
- `TZ=Europe/Warsaw`
- `WEBSITE_TIME_ZONE=Europe/Warsaw`

Notes:

- Queue payloads are sent as raw JSON; this requires `host.json` queue setting `messageEncoding: "none"`.
- `DomainStorage` is intentionally separate from `AzureWebJobsStorage`.
- Collect durable payloads are defined schema-first under `src/main/resources/schemajson/collect-flow/`, generated during `generate-sources`, and validated against their schemas after receive and before send.

## Local development

Prerequisites:

- Java 25
- Maven 3.9+
- Azure Functions Core Tools v4
- Azurite (recommended for local storage)

### 1) Prepare local settings

1. Copy [local.settings.json.example](local.settings.json.example) to `local.settings.json`.
2. Fill required keys listed above.

### 2) Start Azurite (if using development storage)

```bash
npm install -g azurite
azurite --silent --location /tmp/azurite
```

### 3) Build and test

```bash
mvn test
mvn -DskipTests package
```

Coverage gate (local quality guard):

```bash
mvn verify
```

`mvn verify` now enforces minimum JaCoCo coverage and fails the build on regression.
Coverage report is generated at `target/site/jacoco/index.html`.

### 4) Run locally

```bash
mvn azure-functions:run
```

Default local host: `http://localhost:7071`

Useful local calls:

- `POST http://localhost:7071/api/Fun_CollectStart`
- `POST http://localhost:7071/api/Fun_FacebookPublishStart`
- `POST http://localhost:7071/api/telegram/webhook`

## Data storage and schema

Liquibase changelog is at `src/main/resources/db/changelog/db.changelog-master.yaml`.

Core tables covered by integration tests include:

- `sejm_daily_digest_item`
- `sejm_publish_log`
- `sejm_interpellation_publish_state`

## Packaging output

Azure Functions Maven packaging output path:

- `target/azure-functions/sejmstream-fun-sejmlive-local/`

## Test coverage snapshot

The test suite includes:

- Use case tests for collect/publish flows.
- Queue retry/dead-letter policy tests for INTERPELLATION publish.
- Trigger configuration tests for Functions adapters.
- Telegram webhook flow tests.
- Liquibase schema integration tests.
- Spring application context startup tests.
